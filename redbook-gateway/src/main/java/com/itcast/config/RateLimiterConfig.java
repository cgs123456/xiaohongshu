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
    @org.springframework.context.annotation.Primary
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
     * 按用户 ID + 请求路径限流（登录请求优先）：比纯 IP 限流更精准，
     * AI 等按用户计费的接口用它，避免同一出口 IP 下多用户互相挤占
     */
    @Bean
    public KeyResolver userPathKeyResolver() {
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();
            // MyGlobalFilter 校验通过后会把解析出的 userId 写入该头
            String userId = request.getHeaders().getFirst("userId");
            String who = (userId != null && !userId.isBlank()) ? "u" + userId : clientIp(request);
            return Mono.just(who + ":" + request.getURI().getPath());
        };
    }

    /**
     * 解析客户端 IP。网关是最外层入口时，X-Forwarded-For / X-Real-IP
     * 由客户端任意伪造（每个请求换一个假头即可绕过限流），只能信任 remoteAddress；
     * 只有明确部署在可信代理之后时才应启用转发头
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String trustedProxy = System.getenv().getOrDefault("TRUST_PROXY_HEADERS", "false");
        if ("true".equalsIgnoreCase(trustedProxy)) {
            String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeaders().getFirst("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return clientIp(request);
    }

    private String clientIp(ServerHttpRequest request) {
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
