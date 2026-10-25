package com.itcast.service;

import com.itcast.result.Result;
import com.itcast.model.pojo.Attention;

import java.util.List;

public interface AttentionService {
    Result<Integer> isAttention(Integer otherId);
    Result<Void> attention(Integer otherId);
    Result<List<Attention>> getAttention(Integer userId);

    /** 某用户的粉丝 id 分页列表（note 服务发布扇出用，内部端点） */
    Result<List<Integer>> getFollowerIds(Integer otherId, Integer page, Integer pageSize);
}
