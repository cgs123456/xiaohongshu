package com.itcast.controller;

import com.itcast.result.Result;
import com.itcast.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody ChatRequest request) {
        String prompt = request.getPrompt();
        if (prompt == null || prompt.length() > 2000) {
            return Result.failure("prompt长度不能超过2000字符");
        }
        return aiService.chat(prompt);
    }

    public static class ChatRequest {
        private String prompt;

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

}