package com.itcast.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 布隆过滤器工具类。
 * add/mightContain 均用单条 Lua 脚本一次往返完成 5 个 bit 的读写——
 * 此前逐 bit 调 SETBIT/GETBIT，首页每条笔记 5 次 RT（20 条/页 = 100 次网络往返）。
 */
@Component
public class BloomFilterUtil {
    // 9600000 bit ≈ 1.2MB，100 万元素量级下约 1% 误判率
    private static final int BITMAP_SIZE = 9600000;
    private static final int[] HASH_SEED = new int[]{5,15,25,35,45};

    /** KEYS[1]=bitmap key，ARGV[1..n]=offset：全部置 1 */
    private static final DefaultRedisScript<String> ADD_SCRIPT = new DefaultRedisScript<>(
            "for i=1,#ARGV do redis.call('setbit', KEYS[1], ARGV[i], 1) end return 'OK'",
            String.class);

    /** KEYS[1]=bitmap key，ARGV[1..n]=offset：任一为 0 返回 0，否则 1 */
    private static final DefaultRedisScript<Long> CONTAIN_SCRIPT = new DefaultRedisScript<>(
            "for i=1,#ARGV do if redis.call('getbit', KEYS[1], ARGV[i]) == 0 then return 0 end end return 1",
            Long.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public int hash(String value, int seed) {
        int result = 0;
        for (char c : value.toCharArray()) {
            result = seed * result + c;
        }
        return (BITMAP_SIZE - 1) & result;
    }

    public void add(String key, String value) {
        // 脚本参数走 RedisTemplate 的 JSON value 序列化器：String 会被序列化成带引号的
        // "\"123\"" 使 setbit 的 offset 解析失败，必须传 Long（序列化为裸数字）
        List<Long> offsets = new ArrayList<>(HASH_SEED.length);
        for (int seed : HASH_SEED) {
            offsets.add((long) hash(value, seed));
        }
        redisTemplate.execute(ADD_SCRIPT, Collections.singletonList(key), offsets.toArray());
    }

    public boolean mightContain(String key, String value) {
        List<Long> offsets = new ArrayList<>(HASH_SEED.length);
        for (int seed : HASH_SEED) {
            offsets.add((long) hash(value, seed));
        }
        Long r = redisTemplate.execute(CONTAIN_SCRIPT, Collections.singletonList(key), offsets.toArray());
        return Long.valueOf(1L).equals(r);
    }
}
