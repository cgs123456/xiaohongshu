package com.itcast.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间内部接口令牌校验（/product/helper/** 等）。
 * 出厂默认令牌直接记录在仓库源码中，未显式配置时一律拒绝，防止默认令牌被外部滥用。
 */
@Component
public class InternalTokenGuard {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenGuard.class);

    private static final String DEFAULT_INTERNAL_TOKEN = "change-me-internal-token";

    @Value("${internal.token:" + DEFAULT_INTERNAL_TOKEN + "}")
    private String internalToken;

    private boolean configured;

    @PostConstruct
    void check() {
        configured = internalToken != null && !DEFAULT_INTERNAL_TOKEN.equals(internalToken);
        if (!configured) {
            log.warn("internal.token 未配置或为默认值，内部 helper 接口已全部禁用；" +
                    "请在环境变量 INTERNAL_TOKEN 中设置随机值，并与 order 服务保持一致");
        }
    }

    /**
     * 常量时间令牌比较，避免时序侧信道
     */
    public boolean isValid(String provided) {
        if (!configured || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                internalToken.getBytes(StandardCharsets.UTF_8));
    }
}
