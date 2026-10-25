package com.itcast.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 调用头传播：
 * 1. 有请求上下文时转发网关注入的 userId / token 头（下游按用户身份处理）；
 * 2. 所有内部调用统一附带 X-Internal-Token（下游 BaseInterceptor 以内部令牌放行）——
 *    免登录白名单接口（如首页浏览）没有 userId 头，此前这类请求触发的内部调用被统一 401。
 */
@Component
@Slf4j
public class FeignHeaderPropagator implements RequestInterceptor {

    private static final String DEFAULT_INTERNAL_TOKEN = "change-me-internal-token";

    @Value("${internal.token:${INTERNAL_TOKEN:}}")
    private String internalToken;

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String userId = request.getHeader("userId");
            if (userId != null && !userId.isBlank()) {
                template.header("userId", userId);
            }
            String token = request.getHeader("token");
            if (token != null && !token.isBlank()) {
                template.header("token", token);
            }
        }
        if (internalToken != null && !internalToken.isBlank() && !DEFAULT_INTERNAL_TOKEN.equals(internalToken)) {
            template.header("X-Internal-Token", internalToken);
        }
    }
}
