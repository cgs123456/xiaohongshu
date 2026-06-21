package com.itcast.client;

import com.itcast.model.pojo.Attention;
import com.itcast.model.pojo.User;
import com.itcast.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient("redbook-service-user")
public interface UserClient {

    @GetMapping("/user/getUserById/{userId}")
    Result<User> getUserById(@PathVariable("userId") Integer userId);

    @GetMapping("/user/getAttention/{userId}")
    Result<List<Attention>> getAttention(@PathVariable("userId") Integer userId);
}
