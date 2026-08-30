package com.itcast.handler.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.List;

/**
 * AI 笔记摘要/标签生成 Handler（责任链 @Order(11)，SaveNoteToEsHandler 之前）。
 *
 * 调用 DeepSeek 读笔记正文，生成：
 *   - 50 字摘要
 *   - 3 个话题标签
 * 结果设置到 NoteDto，由 SaveNoteToEsHandler 同步到 ES 用于搜索增强。
 *
 * 生成失败降级为不生成（不阻塞发布主流程）。
 */
@Component
@Order(11)
@Slf4j
public class AiSummaryHandler extends NoteHandler {

    @Value("${deepseek.api_url}")
    private String apiUrl;

    @Value("${deepseek.api_key}")
    private String apiKey;

    private final Gson gson = new Gson();

    /** HttpClient 静态复用，避免每次请求都新建 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        String title = noteDto.getTitle();
        String content = noteDto.getContent();

        // 文本为空则跳过
        if ((title == null || title.trim().isEmpty())
                && (content == null || content.trim().isEmpty())) {
            log.debug("笔记标题和内容均为空，跳过 AI 摘要生成");
            return;
        }

        try {
            AiSummaryResult result = generate(title, content);
            if (result == null) {
                log.warn("AI 摘要生成失败，降级为不生成 noteId={}", noteDto.getId());
                return;
            }

            if (result.summary != null && !result.summary.trim().isEmpty()) {
                noteDto.setSummary(result.summary.trim());
            }
            if (result.tags != null && !result.tags.isEmpty()) {
                noteDto.setAiTags(result.tags);
            }
            log.info("AI 摘要生成成功 noteId={} summary={} tags={}",
                    noteDto.getId(), result.summary, result.tags);
        } catch (Exception e) {
            // 生成异常降级为不生成，不阻塞发布
            log.warn("AI 摘要生成异常，降级为不生成 noteId={}", noteDto.getId(), e);
        }
    }

    /** 调用 DeepSeek 生成摘要 + 标签 */
    private AiSummaryResult generate(String title, String content) {
        String prompt = String.format(
                "你是笔记摘要助手。请阅读以下笔记，生成 50 字以内的摘要和 3 个话题标签。\n" +
                "只返回 JSON，不要输出其他内容。\n" +
                "返回格式：{\"summary\":\"50字以内摘要\",\"tags\":[\"标签1\",\"标签2\",\"标签3\"]}\n\n" +
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
            // 使用 Gson 构建 JSON 请求体（避免 String.format 手拼导致转义问题）
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "deepseek-chat");
            JsonArray messages = new JsonArray();
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", "You are a note summary assistant.");
            messages.add(systemMsg);
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);
            requestBody.add("messages", messages);
            requestBody.addProperty("temperature", 0.3);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            // 复用静态 HttpClient
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                return resp.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            } else {
                log.error("DeepSeek 摘要生成请求失败: {} - {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("DeepSeek 摘要生成调用异常", e);
            return null;
        }
    }

    /** 解析 LLM 返回的 JSON */
    private AiSummaryResult parseResult(String text) {
        try {
            String json = extractJson(text);
            if (json == null) return null;

            JsonObject obj = gson.fromJson(json, JsonObject.class);
            AiSummaryResult result = new AiSummaryResult();
            if (obj.has("summary")) {
                result.summary = obj.get("summary").getAsString();
            }
            if (obj.has("tags") && obj.get("tags").isJsonArray()) {
                JsonArray tagArr = obj.getAsJsonArray("tags");
                List<String> tags = new ArrayList<>(tagArr.size());
                for (int i = 0; i < tagArr.size() && i < 3; i++) {
                    tags.add(tagArr.get(i).getAsString());
                }
                result.tags = tags;
            }
            return result;
        } catch (JsonSyntaxException e) {
            log.warn("摘要结果解析失败: {}", text);
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

    /** AI 摘要生成结果 DTO */
    private static class AiSummaryResult {
        String summary;
        List<String> tags;
    }
}
