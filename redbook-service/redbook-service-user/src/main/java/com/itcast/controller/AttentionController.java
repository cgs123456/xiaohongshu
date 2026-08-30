package com.itcast.controller;

import com.itcast.result.Result;
import com.itcast.service.AttentionService;
import com.itcast.model.pojo.Attention;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
public class AttentionController {

    @Autowired
    private AttentionService attentionService;

    @GetMapping("/isAttention/{otherId}")
    public Result<Integer> isAttention(@PathVariable("otherId") Integer otherId){
        return attentionService.isAttention(otherId);
    }

    /**
     * 关注/取关改为 POST（写操作不应使用 GET）
     */
    @PostMapping("/attention/{otherId}")
    public Result<Void> attention(@PathVariable("otherId") Integer otherId) {
        return attentionService.attention(otherId);
    }

    @GetMapping("/getAttention/{userId}")
    public Result<List<Attention>> getAttention(@PathVariable("userId") Integer userId) {
        return attentionService.getAttention(userId);
    }
}
