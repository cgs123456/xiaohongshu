package com.itcast.util;

import java.security.SecureRandom;

/**
 * 验证码工具类
 */
public class CodeUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
