package com.itcast.client;

import com.itcast.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CommentClient 降级类：评论服务不可用时返回默认值，避免调用方 500
 */
@Component
@Slf4j
public class CommentClientFallback implements CommentClient {

    @Override
    public Result<Integer> getCommentCount(Long noteId) {
        log.warn("评论服务不可用，getCommentCount 降级返回 0, noteId={}", noteId);
        return Result.success(0);
    }
}
