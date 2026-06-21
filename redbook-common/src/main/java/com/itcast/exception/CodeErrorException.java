package com.itcast.exception;

/**
 * 验证码错误异常
 */
public class CodeErrorException extends RuntimeException {
    public CodeErrorException(String message) {
        super(message);
    }
}
