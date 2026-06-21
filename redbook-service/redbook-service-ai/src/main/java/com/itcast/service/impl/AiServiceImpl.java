package com.itcast.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.itcast.result.Result;
import com.itcast.service.AiService;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class AiServiceImpl implements AiService {

    @Value("${deepseek.api_url}")
    private String apiUrl;

    @Value("${deepseek.api_key}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();

    @Override
    public Result<String> chat(String prompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "deepseek-chat");

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);
        requestBody.add("messages", messages);

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(apiUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Result.failure("DeepSeek API 调用失败: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            String content = choices.get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            return Result.success(content);
        } catch (IOException e) {
            return Result.failure("DeepSeek API 调用异常: " + e.getMessage());
        }
    }

}