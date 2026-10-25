package com.itcast.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 笔记 AI 增强服务：分类 / 摘要+标签 两类 LLM 调用的统一入口。
 *
 * 发布链路曾把 3 次同步 LLM 调用串行压在 Tomcat 线程上（各 15s 超时，最坏 45s+）；
 * 现由 AiEnrichmentLaunchHandler 在链头并行发射、AiEnrichmentJoinHandler 在落库后有界等待。
 * 本类只负责"发一个异步任务"，不感知责任链。
 */
@Service
@Slf4j
public class AiEnrichmentService {

    /** 笔记分类表——必须与前端 HomePage 的 categories、后端 getNoteList?type= 过滤值保持一致 */
    private static final List<String> TYPE_LIST = Arrays.asList(
            "穿搭", "美妆", "美食", "旅行", "家居", "影视", "职场", "游戏", "汽车", "运动", "摄影", "情感", "生活", "婚庆");

    /** LLM 调用 HTTP 超时（秒） */
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(10);

    /** HttpClient 静态复用，避免每次请求都新建 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** AI 增强专用线程池：每次发布 3 个任务并行，池满时排队而不是占用业务线程 */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "note-ai-enrichment");
        t.setDaemon(true);
        return t;
    });

    @Value("${deepseek.api_url}")
    private String apiUrl;

    @Value("${deepseek.api_key}")
    private String apiKey;

    private final Gson gson = new Gson();

    /**
     * 异步生成笔记分类（LLM 输出归一化到预定义类别，失败返回 null）
     */
    public CompletableFuture<String> generateType(String title, String content) {
        String typeListString = String.join("、", TYPE_LIST);
        String prompt = String.format(
                "你是一个专业的文本分类助手。请根据以下标题和内容，在 %s 这些类别中选择最合适的一个，并且只返回该类别的名称，不要提供任何额外的解释或回答。" +
                        "标题：%s内容：%s请只返回类别名称。",
                typeListString, title == null ? "" : title, content == null ? "" : content);
        return CompletableFuture.supplyAsync(() -> {
            String raw = callDeepSeek(prompt, "You are a helpful assistant.", 0.7);
            if (raw == null) {
                return null;
            }
            final String trimmed = raw.trim();
            return TYPE_LIST.stream().filter(trimmed::contains).findFirst().orElse(null);
        }, EXECUTOR);
    }

    /**
     * 异步生成摘要 + 标签（失败返回 null）
     */
    public CompletableFuture<SummaryResult> generateSummary(String title, String content) {
        String prompt = String.format(
                "你是笔记摘要助手。请阅读以下笔记，生成 50 字以内的摘要和 3 个话题标签。\n" +
                "只返回 JSON，不要输出其他内容。\n" +
                "返回格式：{\"summary\":\"50字以内摘要\",\"tags\":[\"标签1\",\"标签2\",\"标签3\"]}\n\n" +
                "标题：%s\n内容：%s",
                title == null ? "" : title,
                content == null ? "" : content
        );
        return CompletableFuture.supplyAsync(() -> {
            String llmOutput = callDeepSeek(prompt, "You are a note summary assistant.", 0.3);
            return llmOutput == null ? null : parseSummary(llmOutput);
        }, EXECUTOR);
    }

    /** 摘要生成结果 */
    @Getter
    public static class SummaryResult {
        private String summary;
        private List<String> tags;

        static SummaryResult of(String summary, List<String> tags) {
            SummaryResult r = new SummaryResult();
            r.summary = summary;
            r.tags = tags;
            return r;
        }
    }
    /**
     * 调用 DeepSeek chat API（apiUrl 形如 https://api.deepseek.com/v1）
     */
    private String callDeepSeek(String userMessage, String systemPrompt, double temperature) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "deepseek-chat");
            JsonArray messages = new JsonArray();
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);
            requestBody.add("messages", messages);
            requestBody.addProperty("temperature", temperature);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(apiUrl) + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(LLM_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                return resp.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            }
            log.error("DeepSeek 请求失败: {} - {}", response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("DeepSeek 调用异常", e);
            return null;
        }
    }

    private SummaryResult parseSummary(String text) {
        try {
            String json = extractJson(text);
            if (json == null) {
                return null;
            }
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            String summary = obj.has("summary") ? obj.get("summary").getAsString() : null;
            List<String> tags = new ArrayList<>();
            if (obj.has("tags") && obj.get("tags").isJsonArray()) {
                JsonArray tagArr = obj.getAsJsonArray("tags");
                for (int i = 0; i < tagArr.size() && i < 3; i++) {
                    tags.add(tagArr.get(i).getAsString());
                }
            }
            return SummaryResult.of(summary, tags);
        } catch (JsonSyntaxException e) {
            log.warn("摘要结果解析失败: {}", text);
            return null;
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf("```json");
        if (start >= 0) {
            int end = text.indexOf("```", start + 7);
            if (end > start) return text.substring(start + 7, end).trim();
        }
        start = text.indexOf("```");
        if (start >= 0) {
            int end = text.indexOf("```", start + 3);
            if (end > start) {
                String candidate = text.substring(start + 3, end).trim();
                if (candidate.startsWith("{")) return candidate;
            }
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed;
        return null;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
