package com.itcast.model.dto;

import lombok.Data;

/**
 * 登录验证参数（POST + JSON body）
 */
@Data
public class LoginVerifyDto {

    /**
     * 手机号
     */
    private String phone;

    /**
     * 验证码
     */
    private String code;
}
