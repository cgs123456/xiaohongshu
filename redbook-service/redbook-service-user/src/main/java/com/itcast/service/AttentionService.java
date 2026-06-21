package com.itcast.service;

import com.itcast.result.Result;
import com.itcast.model.pojo.Attention;

import java.util.List;

public interface AttentionService {
    Result<Integer> isAttention(Integer otherId);
    Result<Void> attention(Integer otherId);
    Result<List<Attention>> getAttention(Integer userId);
}
