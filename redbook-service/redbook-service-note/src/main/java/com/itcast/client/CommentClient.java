package com.itcast.client;

import com.itcast.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 评论服务 Feign Client，配置 fallback 降级
 * 注意：fallback 生效需要 classpath 有 spring-cloud-starter-circuitbreaker-resilience4j
 * 并开启 spring.cloud.openfeign.circuitbreaker.enabled=true
 */
@FeignClient(value = "redbook-service-comment", fallback = CommentClientFallback.class)
public interface CommentClient {

    @GetMapping("/comment/getCommentCount/{noteId}")
    Result<Integer> getCommentCount(@PathVariable Long noteId);
}
