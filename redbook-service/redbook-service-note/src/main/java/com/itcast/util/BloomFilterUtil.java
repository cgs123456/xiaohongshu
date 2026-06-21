package com.itcast.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 布隆过滤器工具类
 */
@Component
public class BloomFilterUtil {
    private static final int BITMAP_SIZE = 100000;
    private static final int[] HASH_SEED = new int[]{5,15,25,35,45};

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
        for (int seed : HASH_SEED) {
            int hashValue = hash(value, seed);
            redisTemplate.opsForValue().setBit(key, hashValue, true);
        }
    }

    public boolean mightContain(String key, String value) {
        for (int seed : HASH_SEED) {
            int hashValue = hash(value, seed);
            if (Boolean.FALSE.equals(redisTemplate.opsForValue().getBit(key, hashValue))) {
                return false;
            }
        }
        return true;
    }
}

