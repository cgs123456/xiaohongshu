package com.itcast.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * 限流 Key 解析器配置
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 按 IP + 请求路径限流
     */
    @Bean
    public KeyResolver ipPathKeyResolver() {
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();
            String ip = resolveClientIp(request);
            String path = request.getURI().getPath();
            return Mono.just(ip + ":" + path);
        };
    }

    /**
     * 解析客户端真实IP（优先 X-Forwarded-For / X-Real-IP，兜底 remoteAddress）
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
