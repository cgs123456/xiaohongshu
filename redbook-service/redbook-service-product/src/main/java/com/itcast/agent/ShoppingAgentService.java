package com.itcast.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.itcast.agent.dto.AgentMessage;
import com.itcast.agent.dto.FunctionCall;
import com.itcast.context.UserContext;
import com.itcast.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 购物 Agent 编排服务。
 *
 * 工作流程：
 * 1. 读取对话历史（ConversationStore）
 * 2. 构建 system prompt（SystemPromptBuilder，含工具说明 + Few-Shot）
 * 3. 循环调用 AI 服务（/ai/agent/chat）：
 *    - 解析 LLM 输出（FunctionCallParser，自动统计格式正确率）
 *    - 若为 FunctionCall：执行工具（ToolExecutor），将结果作为 tool 消息注入，继续循环
 *    - 若为自然语言：作为最终回复，结束循环
 * 4. 最多 5 轮工具调用，防止死循环
 * 5. 保存对话历史 + 记录 Few-Shot 样本（成功/失败）
 */
@Service
@Slf4j
public class ShoppingAgentService {

    @Autowired
    private ConversationStore conversationStore;

    @Autowired
    private SystemPromptBuilder systemPromptBuilder;

    @Autowired
    private FunctionCallParser functionCallParser;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private FewShotStore fewShotStore;

    /** 下单确认状态（按用户）：pending 表示 Agent 已发起下单待用户表态，confirmed 表示用户已在新一轮回复中表态 */
    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    public static final String ORDER_PENDING_KEY = "agent:order:pending:";
    public static final String ORDER_CONFIRMED_KEY = "agent:order:confirmed:";
    private static final java.time.Duration CONFIRM_TTL = java.time.Duration.ofMinutes(10);

    /** AI 服务 agent/chat 接口地址（直连 ai 服务，绕过网关——内部调用不带用户 token，走网关会被鉴权过滤器拒绝） */
    @Value("${agent.ai-chat-url:http://localhost:8091/ai/agent/chat}")
    private String aiChatUrl;

    /** 最大工具调用轮数（防止死循环） */
    private static final int MAX_TOOL_ROUNDS = 5;

    private final Gson gson = new Gson();

    /**
     * 处理用户购物请求
     * @param conversationId 对话 ID（前端传入；为空则新建）
     * @param userMessage 用户消息
     * @return Agent 最终回复
     */
    public String chat(String conversationId, String userMessage) {
        // 1. 会话 ID 兜底
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = UUID.randomUUID().toString().replace("-", "");
        }
        // 会话按用户隔离，防止拿到/猜到 conversationId 读写他人对话
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return "请先登录后再使用购物助手。";
        }

        // 2. 读取历史 + 追加当前用户消息
        List<AgentMessage> history = conversationStore.getHistory(userId, conversationId);
        history.add(new AgentMessage("user", userMessage));

        // 2.5 下单确认协议：上一轮 Agent 询问过"是否下单"（pending 标记存在），
        //     用户本轮的回复即视为表态——置 confirmed 标记，由 LLM 根据用户答复内容
        //     决定是否真的调用 create_order（否定答复时 LLM 不应调用）
        try {
            String pendingKey = ORDER_PENDING_KEY + userId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey))) {
                redisTemplate.delete(pendingKey);
                redisTemplate.opsForValue().set(ORDER_CONFIRMED_KEY + userId, "1", CONFIRM_TTL);
            }
        } catch (Exception e) {
            log.warn("处理下单确认状态失败，userId={}", userId, e);
        }

        // 3. 构建 system prompt（工具说明 + Few-Shot）
        String systemPrompt = systemPromptBuilder.build(userId);

        // 4. 编排循环
        String finalReply = null;
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            String llmOutput = callAi(systemPrompt, history);
            if (llmOutput == null) {
                finalReply = "AI 服务暂不可用，请稍后再试。";
                break;
            }

            // 解析是否为 FunctionCall（自动统计格式正确率）
            FunctionCall call = functionCallParser.parse(llmOutput);

            if (call == null) {
                // 区分两种情况：
                // A) 自然语言回复 → 最终答案
                // B) 格式错误的函数调用尝试 → 记录失败样本 + 提示 LLM 重试
                if (functionCallParser.looksLikeFunctionCallAttempt(llmOutput)) {
                    // 情况 B：记录失败样本到 Few-Shot 池（用于后续注入 system prompt 提升正确率）
                    fewShotStore.recordSample(userId, userMessage, llmOutput, false);
                    log.warn("Agent 输出格式错误，提示重试 round={} output={}", round, llmOutput);
                    history.add(new AgentMessage("assistant", llmOutput));
                    history.add(new AgentMessage("tool",
                            "格式错误：请输出合法的 JSON 函数调用对象，格式如 {\"name\":\"工具名\",\"arguments\":{...}}"));
                    continue;  // 继续循环让 LLM 重试
                }
                // 情况 A：自然语言回复 → 最终答案
                finalReply = llmOutput;
                history.add(new AgentMessage("assistant", llmOutput));
                break;
            }

            // 是 FunctionCall → 记录为成功样本
            fewShotStore.recordSample(userId, userMessage, llmOutput, true);

            // 执行工具
            String toolResult = toolExecutor.execute(call);
            log.info("Agent 工具调用 round={} name={} result={}", round, call.getName(), toolResult);

            // 注入对话历史：assistant 消息（LLM 输出） + tool 消息（工具结果）
            history.add(new AgentMessage("assistant", llmOutput));
            history.add(new AgentMessage("tool", toolResult));

            // 继续下一轮，让 LLM 看到工具结果后决定下一步
        }

        // 5. 达到最大轮数仍未给出最终回复
        if (finalReply == null) {
            finalReply = "已达到最大工具调用次数（" + MAX_TOOL_ROUNDS + "），请换一种方式描述您的需求。";
        }

        // 6. 保存对话历史
        conversationStore.appendMessages(userId, conversationId, history);

        log.info("Agent 对话完成 conversationId={} finalReply={}", conversationId, finalReply);
        return finalReply;
    }

    /**
     * 调用 AI 服务的 /ai/agent/chat 接口
     * @return LLM 输出文本；失败返回 null
     */
    private String callAi(String systemPrompt, List<AgentMessage> messages) {
        try {
            Integer userId = UserContext.getUserId();

            // 构建请求体（与 AgentChatDto 结构一致）
            Map<String, Object> request = new HashMap<>();
            request.put("systemPrompt", systemPrompt);
            List<Map<String, String>> msgList = new ArrayList<>();
            for (AgentMessage m : messages) {
                msgList.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
            request.put("messages", msgList);

            String response = HttpUtil.postWithUserId(aiChatUrl, request, userId);
            if (response == null) {
                log.error("AI 服务返回空响应，url={}", aiChatUrl);
                return null;
            }

            // 解析 Result<String> 响应
            JsonObject resp = gson.fromJson(response, JsonObject.class);
            if (!resp.has("code")) {
                log.error("AI 服务响应格式异常: {}", response);
                return null;
            }
            int code = resp.get("code").getAsInt();
            if (code != 200) {
                log.error("AI 服务返回失败: code={} message={}", code,
                        resp.has("message") ? resp.get("message").getAsString() : "");
                return null;
            }
            return resp.get("data").getAsString();
        } catch (Exception e) {
            log.error("调用 AI 服务异常 url={}", aiChatUrl, e);
            return null;
        }
    }
}
