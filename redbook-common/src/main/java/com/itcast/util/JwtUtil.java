package com.itcast.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.security.SecureRandom;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jwt工具类
 */
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /**
     * 优先读取环境变量 JWT_SECRET_KEY；未配置时每次启动生成随机密钥，
     * 保证源码泄露也无法伪造 token（代价是重启后所有 token 失效）。
     */
    private static final String SECRET_KEY = initSecretKey();

    // 24小时过期
    private static final long EXPIRE_TIME = 24L * 60 * 60 * 1000;

    private static String initSecretKey() {
        String env = System.getenv("JWT_SECRET_KEY");
        if (env != null && env.length() >= 32) {
            return env;
        }
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        String randomKey = sb.toString();
        log.warn("未配置 JWT_SECRET_KEY 环境变量，已使用本次启动随机密钥（重启后所有token失效）。生产环境必须配置该变量！");
        return randomKey;
    }

    /**
     * 生成token
     * @param userId
     * @return
     */
    public static String createToken(Integer userId) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .sign(algorithm);
    }

    /**
     * 解析token
     * @param token
     * @return
     */
    public static String parseToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        return JWT.require(algorithm)
                .build()
                .verify(token)
                .getSubject();
    }
}
