package com.itcast.interceptor;

import com.itcast.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 基础拦截器 - 提供公共拦截逻辑
 *
 * 放行条件（二选一）：
 * 1. 网关注入的 userId 头（外部请求路径）
 * 2. 有效的 X-Internal-Token（服务间 Feign 直连路径——MQ 消费线程等无请求上下文的调用
 *    无法携带 userId，此前内部接口全部被 401 拒绝，跨服务批量查用户一直静默降级为空）
 */
@Slf4j
public class BaseInterceptor implements HandlerInterceptor {

    private static final String INTERNAL_TOKEN_ENV = "INTERNAL_TOKEN";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取用户id（网关鉴权后注入）
        String userId = request.getHeader("userId");
        if (userId != null) {
            log.info("用户id为：{}", userId);
            UserContext.setUserId(Integer.valueOf(userId));
            return true;
        }

        // 2. 服务间内部调用：校验内部令牌
        if (isValidInternalToken(request.getHeader("X-Internal-Token"))) {
            log.info("内部令牌校验通过，path={}", request.getRequestURI());
            return true;
        }

        log.warn("请求缺少 userId 与有效内部令牌，path={}", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }

    private boolean isValidInternalToken(String provided) {
        if (provided == null) {
            return false;
        }
        String expected = System.getenv(INTERNAL_TOKEN_ENV);
        // 内部令牌未配置时一律拒绝（与 product 服务 InternalTokenGuard 的默认熔断策略一致）
        if (expected == null || expected.isBlank() || "change-me-internal-token".equals(expected)) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
