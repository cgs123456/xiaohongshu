package com.itcast.handler.impl;

import com.google.gson.Gson;
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
@Order(5)
@Slf4j
public class GetNoteTypeHandler extends NoteHandler {

    private final List<String> typeList = Arrays.asList("情感", "体育", "穿搭", "美食");

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
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
    }

    @Value("${deepseek.api_url}")
    private String apiUrl;

    @Value("${deepseek.api_key}")
    private String apiKey;

    @SuppressWarnings("unchecked")
    protected String sendRequest(String userMessage) {
        try {
            // 1. 创建 HttpClient
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // 2. 构造 JSON 请求体
            String requestBody = String.format(
                    "{ \"model\": \"deepseek-chat\", \"messages\": [ " +
                            "{\"role\": \"system\", \"content\": \"You are a helpful assistant.\"}, " +
                            "{\"role\": \"user\", \"content\": \"%s\"} " +
                            "], \"temperature\": 0.7 }",
                    userMessage
            );

            // 3. 创建 HTTP 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15)) // 请求超时时间
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // 4. 发送请求并获取响应
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 5. 处理响应
            if (response.statusCode() == 200) {
                Type typeOfHashMap = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> responseMap = new Gson().fromJson(response.body(), typeOfHashMap);
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
