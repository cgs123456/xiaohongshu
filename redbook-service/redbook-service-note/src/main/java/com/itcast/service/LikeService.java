package com.itcast.service;

import com.itcast.model.vo.LikeVo;
import com.itcast.result.Result;

import java.util.List;

public interface LikeService {
    Result<Void> like(Long noteId);

    Result<Boolean> isLike(Long noteId);

    Result<List<LikeVo>> getLikeList();
}
