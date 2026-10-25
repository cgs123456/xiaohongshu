package com.itcast.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 布隆过滤器工具类。
 * add 单值写入；mightContainBatch 整页批量判定——均用单条 Lua 脚本一次往返完成
 * 每值 5 个 bit 的读写（此前逐值 EVAL，20 条/页 = 20 次网络往返）。
 * 参数经 RedisTemplate 的 JSON value 序列化器：offset 必须传 Long（String 会被加引号致 getbit 解析失败）。
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

    /**
     * KEYS[1]=bitmap key，ARGV 按每 5 个一组对应一个值（Java 侧哈希后的 offset）：
     * 组内任一 bit 为 0 视为不存在，返回每组 0/1 的数组
     */
    private static final DefaultRedisScript<List> CONTAIN_BATCH_SCRIPT = new DefaultRedisScript<>(
            "local res = {} local n = math.floor(#ARGV / 5) " +
            "for i = 0, n - 1 do local present = 1 " +
            "for j = 1, 5 do if redis.call('getbit', KEYS[1], tonumber(ARGV[i * 5 + j])) == 0 then present = 0 break end end " +
            "res[i + 1] = present end return res",
            List.class);

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
        List<Long> offsets = new ArrayList<>(HASH_SEED.length);
        for (int seed : HASH_SEED) {
            offsets.add((long) hash(value, seed));
        }
        redisTemplate.execute(ADD_SCRIPT, Collections.singletonList(key), offsets.toArray());
    }

    /**
     * 整页批量判定：一次 EVAL 返回所有值的存在性。
     * @return 判定为"可能已存在"的值集合（布隆语义：存在=可能存在，不存在=一定不存在）
     */
    @SuppressWarnings("unchecked")
    public Set<String> mightContainBatch(String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> offsets = new ArrayList<>(values.size() * HASH_SEED.length);
        for (String value : values) {
            for (int seed : HASH_SEED) {
                offsets.add((long) hash(value, seed));
            }
        }
        List<Long> flags = redisTemplate.execute(CONTAIN_BATCH_SCRIPT, Collections.singletonList(key), offsets.toArray());
        Set<String> present = new HashSet<>();
        if (flags != null) {
            for (int i = 0; i < values.size() && i < flags.size(); i++) {
                if (flags.get(i) != null && flags.get(i) == 1L) {
                    present.add(values.get(i));
                }
            }
        }
        return present;
    }
}
