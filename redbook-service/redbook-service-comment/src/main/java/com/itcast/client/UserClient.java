package com.itcast.client;

import com.itcast.model.pojo.User;
import com.itcast.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient("redbook-service-user")
public interface UserClient {

    @GetMapping("/user/getUserById/{userId}")
    Result<User> getUserById(@PathVariable("userId") Integer userId);

    /**
     * 批量查询用户（ids 为逗号分隔字符串，如 "1,2,3"）
     */
    @GetMapping("/user/getUserByIds")
    Result<List<User>> getUserByIds(@RequestParam("ids") String ids);
}
