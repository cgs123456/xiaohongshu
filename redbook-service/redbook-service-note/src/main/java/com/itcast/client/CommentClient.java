package com.itcast.client;

import com.itcast.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("redbook-service-comment")
public interface CommentClient {

    @GetMapping("/comment/getCommentCount/{noteId}")
    Result<Integer> getCommentCount(@PathVariable Long noteId);
}
