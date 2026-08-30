package com.itcast.controller;

import com.itcast.model.dto.LoginVerifyDto;
import com.itcast.result.Result;
import com.itcast.service.LoginService;
import com.itcast.model.dto.LoginDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class LoginController {

    @Autowired
    private LoginService loginService;

    /**
     * 发送验证码（POST + JSON body，防 GET 请求把手机号记录到访问日志）
     */
    @PostMapping("/send")
    public Result<String> send(@RequestBody LoginVerifyDto loginVerifyDto) {
        return loginService.send(loginVerifyDto.getPhone());
    }

    /**
     * 校验验证码并登录（POST + JSON body）
     */
    @PostMapping("/verify")
    public Result<String> verify(@RequestBody LoginDto loginDto) {
        return loginService.verify(loginDto);
    }
}
