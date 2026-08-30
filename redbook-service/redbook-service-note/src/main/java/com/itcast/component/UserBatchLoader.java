package com.itcast.component;

import com.itcast.client.UserClient;
import com.itcast.model.pojo.User;
import com.itcast.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批量获取用户信息的公共组件：优先一次 getUserByIds，失败降级为逐个查询。
 * 供各笔记列表策略类与点赞列表复用，避免每处复制一份降级逻辑。
 */
@Component
@Slf4j
public class UserBatchLoader {

    @Autowired
    private UserClient userClient;

    public Map<Integer, User> batchGetUsers(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Result<List<User>> result = userClient.getUserByIds(userIds);
            List<User> users = result.getData();
            if (users != null && !users.isEmpty()) {
                return users.stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.error("批量获取用户信息失败，降级为逐个查询", e);
        }
        // 降级：逐个查询
        Map<Integer, User> userMap = new HashMap<>();
        for (Integer uid : userIds) {
            try {
                User user = userClient.getUserById(uid).getData();
                if (user != null) {
                    userMap.put(uid, user);
                }
            } catch (Exception e) {
                log.error("获取用户信息失败 userId={}", uid, e);
            }
        }
        return userMap;
    }
}
