package com.itcast.handler.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Order(6)
@Slf4j
public class GetNoteTypeHandler extends NoteHandler {

    private final List<String> typeList = Arrays.asList("情感", "体育", "穿搭", "美食");

    /**
     * HttpClient 静态复用，避免每次请求都新建
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Gson gson = new Gson();

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        // 纯增强步骤：允许失败不阻断发布链路
        try {
            // 使用 String.join() 将列表转换为字符串
            String typeListString = String.join("、", typeList);

            // 生成最终的提示词
            String prompt = String.format(
                    "你是一个专业的文本分类助手。请根据以下标题和内容，在 %s 这些类别中选择最合适的一个，并且只返回该类别的名称，不要提供任何额外的解释或回答。" +
                            "标题：%s" +
                            "内容：%s" +
                            "请只返回类别名称。",
                    typeListString, noteDto.getTitle(), noteDto.getContent());

            // 发送请求并获取响应
            String type = sendRequest(prompt);
            noteDto.setType(type);
            log.info("===已设置笔记类型{}===", type);
        } catch (Exception e) {
            // 生成失败降级为不设置类型，不阻断发布
            log.error("笔记类型生成失败，不阻断发布 noteId={}", noteDto.getId(), e);
        }
    }

    @Value("${deepseek.api_url}")
    private String apiUrl;

    @Value("${deepseek.api_key}")
    private String apiKey;

    @SuppressWarnings("unchecked")
    protected String sendRequest(String userMessage) {
        try {
            // 1.使用 Gson 构建 JSON 请求体（避免 String.format 手拼导致转义问题）
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "deepseek-chat");
            JsonArray messages = new JsonArray();
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", "You are a helpful assistant.");
            messages.add(systemMsg);
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);
            requestBody.add("messages", messages);
            requestBody.addProperty("temperature", 0.7);

            // 2.创建 HTTP 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15)) // 请求超时时间
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            // 3.发送请求并获取响应（复用静态 HttpClient）
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            // 4.处理响应
            if (response.statusCode() == 200) {
                Type typeOfHashMap = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> responseMap = gson.fromJson(response.body(), typeOfHashMap);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                Map<String, Object> message = choices.get(0);
                Map<String, Object> messageObj = (Map<String, Object>) message.get("message");
                return (String) messageObj.get("content");
            } else {
                log.error("Request failed: {} - {}",response.statusCode(),response.body());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }
}
