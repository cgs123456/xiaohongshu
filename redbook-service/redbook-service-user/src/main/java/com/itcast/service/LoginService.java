package com.itcast.service;

import com.itcast.result.Result;
import com.itcast.model.dto.LoginDto;

public interface LoginService {
    Result<String> send(String phone);

    Result<String> verify(LoginDto loginDto);
}
