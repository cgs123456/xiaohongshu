package com.itcast.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.itcast.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Few-Shot 样本池（Redis，按用户隔离）。
 * - 成功样本：可正确解析为 FunctionCall 的 (query, output) 对
 * - 失败样本：格式错误的 (query, output) 对
 * - 成功样本最多保留 50 条，用于注入该用户自己的 system prompt 提升格式正确率
 * - 按用户隔离：样本含用户原始提问，注入全局池会把 A 用户的内容泄露进
 *   B 用户的提示词（可能被 LLM 复述），因此 key 必须带 userId
 */
@Component
@Slf4j
public class FewShotStore {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final Gson gson = new Gson();

    private static final int MAX_SUCCESS_SAMPLES = 50;
    private static final int MAX_FAILURE_SAMPLES = 20;

    /**
     * 记录一条样本
     * @param userId 样本归属用户（null 时不记录，避免落入共享池）
     * @param query 用户原始问题
     * @param output LLM 原始输出
     * @param success 是否成功解析为 FunctionCall
     */
    public void recordSample(Integer userId, String query, String output, boolean success) {
        if (userId == null) {
            return;
        }
        try {
            JsonObject sample = new JsonObject();
            sample.addProperty("query", query);
            sample.addProperty("output", output);
            String json = gson.toJson(sample);

            String key = success
                    ? RedisConstant.AGENT_FEWSHOT_SUCCESS + ":" + userId
                    : RedisConstant.AGENT_FEWSHOT_FAILURE + ":" + userId;

            // 左插（最新的在前面）
            redisTemplate.opsForList().leftPush(key, json);
            // 修剪保留最近 N 条
            redisTemplate.opsForList().trim(key, 0, success ? MAX_SUCCESS_SAMPLES - 1 : MAX_FAILURE_SAMPLES - 1);
        } catch (Exception e) {
            log.error("记录 Few-Shot 样本失败", e);
        }
    }

    /**
     * 获取该用户最近的 N 条成功样本（仅注入其本人的 system prompt）
     */
    public List<String> getSuccessSamples(Integer userId, int limit) {
        List<String> result = new ArrayList<>();
        if (userId == null) {
            return result;
        }
        try {
            List<Object> raw = redisTemplate.opsForList()
                    .range(RedisConstant.AGENT_FEWSHOT_SUCCESS + ":" + userId, 0, limit - 1);
            if (raw == null) {
                return result;
            }
            for (Object o : raw) {
                result.add(o.toString());
            }
        } catch (Exception e) {
            log.error("读取 Few-Shot 样本失败", e);
        }
        return result;
    }
}
