package com.itcast.agent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itcast.agent.dto.AgentMessage;
import com.itcast.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多轮对话存储（Redis）。
 * - key: agent:conversation:{userId}:{conversationId}（按用户隔离，防止跨用户读取/续写他人对话）
 * - TTL: 30 分钟，每次读写刷新
 * - 滑动窗口：最多保留 MAX_TURNS*2 条消息（10 轮），超出则截断保留最近的
 */
@Component
@Slf4j
public class ConversationStore {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private org.redisson.api.RedissonClient redissonClient;

    private final Gson gson = new Gson();
    private static final Type MESSAGE_LIST_TYPE = new TypeToken<List<AgentMessage>>() {}.getType();

    /** 最大保留轮数（一问一答为一轮） */
    private static final int MAX_TURNS = 10;
    /** 每轮 2 条消息（user + assistant） */
    private static final int MAX_MESSAGES = MAX_TURNS * 2;
    /** TTL 30 分钟（秒） */
    private static final long TTL_SECONDS = 30 * 60;

    /**
     * 读取对话历史
     */
    public List<AgentMessage> getHistory(Integer userId, String conversationId) {
        if (userId == null || conversationId == null || conversationId.isEmpty()) {
            return new ArrayList<>();
        }
        String key = conversationKey(userId, conversationId);
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return new ArrayList<>();
        }
        try {
            List<AgentMessage> messages = gson.fromJson(val.toString(), MESSAGE_LIST_TYPE);
            // 刷新 TTL
            redisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
            return messages != null ? messages : new ArrayList<>();
        } catch (Exception e) {
            log.error("读取对话历史失败: conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 追加一条消息并保存（自动滑动窗口截断）。
     * 读改写全程持锁：同会话并发请求互相覆盖会丢消息
     */
    public void appendMessage(Integer userId, String conversationId, AgentMessage message) {
        if (userId == null || conversationId == null || conversationId.isEmpty() || message == null) {
            return;
        }
        org.redisson.api.RLock lock = redissonClient.getLock("agent:conv:lock:" + userId + ":" + conversationId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取会话锁失败，放弃本条消息追加，conversationId={}", conversationId);
                return;
            }
            List<AgentMessage> history = getHistory(userId, conversationId);
            history.add(message);
            trimAndSave(userId, conversationId, history);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取会话锁被中断，conversationId={}", conversationId, e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    /**
     * 批量追加消息（同样持锁，防止同会话并发覆盖丢消息）
     */
    public void appendMessages(Integer userId, String conversationId, List<AgentMessage> messages) {
        if (userId == null || conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }
        org.redisson.api.RLock lock = redissonClient.getLock("agent:conv:lock:" + userId + ":" + conversationId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取会话锁失败，放弃本批消息追加，conversationId={}", conversationId);
                return;
            }
            List<AgentMessage> history = getHistory(userId, conversationId);
            history.addAll(messages);
            trimAndSave(userId, conversationId, history);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取会话锁被中断，conversationId={}", conversationId, e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    private void trimAndSave(Integer userId, String conversationId, List<AgentMessage> history) {
        if (history.size() > MAX_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_MESSAGES, history.size()));
        }
        save(userId, conversationId, history);
    }

    /**
     * 清空对话历史（话题切换时调用）
     */
    public void clear(Integer userId, String conversationId) {
        if (userId == null || conversationId == null || conversationId.isEmpty()) {
            return;
        }
        redisTemplate.delete(conversationKey(userId, conversationId));
    }

    private void save(Integer userId, String conversationId, List<AgentMessage> messages) {
        String key = conversationKey(userId, conversationId);
        redisTemplate.opsForValue().set(key, gson.toJson(messages), TTL_SECONDS, TimeUnit.SECONDS);
    }

    private String conversationKey(Integer userId, String conversationId) {
        return RedisConstant.AGENT_CONVERSATION + userId + ":" + conversationId;
    }
}
