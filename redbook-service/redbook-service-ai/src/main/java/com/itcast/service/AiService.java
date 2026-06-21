package com.itcast.service;

import com.itcast.result.Result;

public interface AiService {

    Result<String> chat(String prompt);

}