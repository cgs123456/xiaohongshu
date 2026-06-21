package com.itcast.exception;

/**
 * 用户不存在异常
 */
public class UserNoExistException extends RuntimeException {
    public UserNoExistException(String message) {
        super(message);
    }
}
