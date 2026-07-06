package com.itcast.handler.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM 内容审核 Handler（责任链 @Order(4)，FilterTitleHandler 之前）。
 *
 * 审核 title + content 文本，检测色情/暴力/广告引流/虚假信息。
 * 置信度三档处理：
 *   > 0.8  自动拦截（抛异常触发 Saga 补偿）
 *   0.5-0.8 标记 PENDING_REVIEW（继续发布流程）
 *   < 0.5  自动放行
 *
 * 审核异常时降级放行，不阻塞发布主流程。
 */
@Component
@Order(4)
@Slf4j
public class ModerationHandler extends NoteHandler {

    @Value("${deepseek.api_url}")
    private String apiUrl;

    @Value("${deepseek.api_key}")
    private String apiKey;

    /** 高置信度拦截阈值 */
    private static final double BLOCK_THRESHOLD = 0.8;
    /** 中置信度人工审核阈值 */
    private static final double REVIEW_THRESHOLD = 0.5;

    private final Gson gson = new Gson();

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        String title = noteDto.getTitle();
        String content = noteDto.getContent();

        // 文本为空则跳过审核
        if ((title == null || title.trim().isEmpty())
                && (content == null || content.trim().isEmpty())) {
            log.debug("笔记标题和内容均为空，跳过审核");
            return;
        }

        try {
            ModerationResult result = moderate(title, content);
            if (result == null) {
                // API 异常或解析失败 → 降级放行
                log.warn("审核服务异常，降级放行 noteId={} title={}", noteDto.getId(), title);
                return;
            }

            log.info("笔记审核结果 noteId={} type={} confidence={} reason={}",
                    noteDto.getId(), result.violationType, result.confidence, result.reason);

            if (result.confidence > BLOCK_THRESHOLD) {
                // 高置信度 → 拦截，抛异常触发 Saga 补偿
                noteDto.setModerationStatus("BLOCKED");
                throw new RuntimeException("内容审核拦截：" + result.reason);
            } else if (result.confidence > REVIEW_THRESHOLD) {
                // 中置信度 → 标记待人工审核，继续发布
                noteDto.setModerationStatus("PENDING_REVIEW");
                log.info("笔记标记待人工审核 noteId={}", noteDto.getId());
            } else {
                // 低置信度 → 放行
                log.info("笔记审核通过 noteId={}", noteDto.getId());
            }
        } catch (RuntimeException e) {
            // 高置信度拦截的异常需要继续抛出，触发补偿
            throw e;
        } catch (Exception e) {
            // 其他异常降级放行
            log.warn("审核过程异常，降级放行 noteId={}", noteDto.getId(), e);
        }
    }

    /**
     * 调用 DeepSeek 审核文本
     * @return ModerationResult；API 异常或解析失败返回 null
     */
    private ModerationResult moderate(String title, String content) {
        String prompt = String.format(
                "你是内容审核助手。请审核以下笔记内容是否违规。只返回 JSON，不要输出其他内容。\n" +
                "违规类型：porn(色情)、violence(暴力)、ad(广告引流)、fake(虚假信息)、none(正常)\n" +
                "返回格式：{\"violation_type\":\"类型\",\"confidence\":0.0到1.0的数字,\"reason\":\"原因\"}\n\n" +
                "标题：%s\n内容：%s",
                title == null ? "" : title,
                content == null ? "" : content
        );

        String llmOutput = callDeepSeek(prompt);
        if (llmOutput == null) {
            return null;
        }

        return parseResult(llmOutput);
    }

    /** 调用 DeepSeek chat API（与 GetNoteTypeHandler 风格一致） */
    private String callDeepSeek(String userMessage) {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String requestBody = String.format(
                    "{ \"model\": \"deepseek-chat\", \"messages\": [ " +
                    "{\"role\": \"system\", \"content\": \"You are a content moderation assistant.\"}, " +
                    "{\"role\": \"user\", \"content\": \"%s\"} " +
                    "], \"temperature\": 0.1 }",
                    userMessage.replace("\"", "\\\"").replace("\n", "\\n")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                return resp.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            } else {
                log.error("DeepSeek 审核请求失败: {} - {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("DeepSeek 审核调用异常", e);
            return null;
        }
    }

    /** 解析 LLM 返回的 JSON 审核结果 */
    private ModerationResult parseResult(String text) {
        try {
            // 提取 JSON（支持 ```json 块和裸 JSON）
            String json = extractJson(text);
            if (json == null) return null;

            JsonObject obj = gson.fromJson(json, JsonObject.class);
            ModerationResult result = new ModerationResult();
            result.violationType = obj.has("violation_type") ? obj.get("violation_type").getAsString() : "none";
            result.confidence = obj.has("confidence") ? obj.get("confidence").getAsDouble() : 0.0;
            result.reason = obj.has("reason") ? obj.get("reason").getAsString() : "";
            return result;
        } catch (JsonSyntaxException e) {
            log.warn("审核结果解析失败: {}", text);
            return null;
        }
    }

    private String extractJson(String text) {
        // 尝试 ```json 块
        int start = text.indexOf("```json");
        if (start >= 0) {
            int end = text.indexOf("```", start + 7);
            if (end > start) return text.substring(start + 7, end).trim();
        }
        // 尝试裸 ``` 块
        start = text.indexOf("```");
        if (start >= 0) {
            int end = text.indexOf("```", start + 3);
            if (end > start) {
                String candidate = text.substring(start + 3, end).trim();
                if (candidate.startsWith("{")) return candidate;
            }
        }
        // 尝试整段
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed;
        return null;
    }

    /** 审核结果 DTO */
    private static class ModerationResult {
        String violationType;  // porn/violence/ad/fake/none
        double confidence;     // 0.0 - 1.0
        String reason;
    }
}
