package com.itcast.service.impl;

import com.itcast.annotation.SendMessage;
import com.itcast.client.CommentClient;
import com.itcast.client.UserClient;
import com.itcast.constant.ExceptionConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.enums.LogType;
import com.itcast.exception.NoteNoExistException;
import com.itcast.exception.UserNoExistException;
import com.itcast.handler.NoteHandler;
import com.itcast.mapper.NoteMapper;
import com.itcast.mapper.NoteScanMapper;
import com.itcast.model.dto.NoteDto;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.NoteBrowse;
import com.itcast.model.pojo.User;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.service.NoteService;
import com.itcast.strategy.GetNotesStrategy;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyFactory;
import com.itcast.strategy.NoteStrategyType;
import com.itcast.util.BloomFilterUtil;
import com.itcast.util.DealTimeUtil;
import com.itcast.util.DiffDayUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    /**
     * 空值占位符（防缓存穿透）
     */
    private static final String EMPTY_PLACEHOLDER = "";
    /**
     * 正常缓存 TTL 基础值（秒）
     */
    private static final long CACHE_TTL_SECONDS = 300L;
    /**
     * 正常缓存 TTL 随机增量（秒），防缓存雪崩
     */
    private static final long CACHE_TTL_RANDOM = 300L;
    /**
     * 空值占位 TTL（秒）
     */
    private static final long EMPTY_TTL_SECONDS = 60L;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteScanMapper noteScanMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    @Autowired
    private CommentClient commentClient;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    @SendMessage(type = LogType.SCAN)
    public Result<NoteVo> getNote(Long noteId) throws ParseException {
        // 1.查询笔记（带缓存、互斥锁、空值占位）
        Note note = getNoteWithCache(noteId);

        // 2.获取发布笔记用户信息
        User user = userClient.getUserById(note.getUserId()).getData();
        if (user == null) {
            throw new UserNoExistException(ExceptionConstant.USER_NO_EXIST);
        }

        // 3.处理时间字符串
        int days = DiffDayUtil.diffDays(
                new SimpleDateFormat("yyyy-MM-dd").parse(note.getTime()), new Date());
        String dealTime = DealTimeUtil.dealTime(days);
        if (StringUtils.isBlank(dealTime)) {
            dealTime = note.getTime();
        }

        // 4.获取评论数，Feign 调用失败降级为 0 而不是抛错
        Integer commentCount = getCommentCountSafe(noteId);

        // 5.设置vo
        NoteVo noteVo = new NoteVo();
        noteVo.setNote(note);
        noteVo.setDealTime(dealTime);
        noteVo.setUser(user);
        noteVo.setComment(commentCount);

        // 6.加入布隆过滤器
        Integer loginUserId = UserContext.getUserId();
        if (loginUserId != null) {
            threadPoolTaskExecutor.execute(() ->
                    bloomFilterUtil.add(RedisConstant.USER_BLOOM_FILTER + loginUserId, noteId.toString()));
        }

        // 7.记录用户访问笔记：(user_id, note_id) 唯一索引，重复浏览捕获 DuplicateKeyException 静默处理
        if (loginUserId != null) {
            try {
                NoteBrowse noteBrowse = new NoteBrowse();
                noteBrowse.setNoteId(noteId);
                noteBrowse.setUserId(loginUserId);
                noteScanMapper.insert(noteBrowse);
            } catch (DuplicateKeyException e) {
                log.debug("用户已经访问过，不需要再次插入 noteId={}, userId={}", noteId, loginUserId);
            }
        }
        return Result.success(noteVo);
    }

    @Autowired
    private NoteStrategyFactory strategyFactory;

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyType strategyType, NoteStrategyContext context) {
        GetNotesStrategy strategy = strategyFactory.getStrategy(strategyType);
        return strategy.getNotes(context);
    }

    @Autowired
    private List<NoteHandler> noteHandlers;

    /**
     * 发布笔记（使用 Saga 补偿型事务）
     * 将执行成功的步骤保存在ThreadLocal中用于后续补偿操作
     * 当某个步骤失败时，会自动回滚已执行的所有步骤
     * 流程：设置用户ID -> 上传图片 -> 获取位置 -> 过滤标题 -> 获取笔记类型 -> 提取话题 -> 保存笔记 -> 保存笔记话题 -> 保存位置到Redis -> 保存到ES
     */
    @Override
    public Result<Void> postNote(NoteDto dto) {
        log.info("发布笔记开始（Saga 补偿型事务）...");

        try {
            // 初始化补偿上下文
            NoteHandler.initCompensationContext();

            // 责任链执行
            Iterator<NoteHandler> iterator = noteHandlers.iterator();
            NoteHandler firstHandler = iterator.next();
            firstHandler.handleWithCompensation(dto, iterator);

            log.info("笔记发布成功，noteId: {}", dto.getId());
            return Result.success(null);
        } catch (Exception e) {
            // 区分业务异常：审核拦截等业务异常把 message 透传给前端，其余异常返回统一失败文案
            String message = e.getMessage();
            if (message != null && message.contains("内容审核拦截")) {
                log.warn("笔记发布被审核拦截，noteId: {}, reason: {}", dto.getId(), message);
                return Result.failure(message);
            }
            log.error("笔记发布失败，已执行补偿操作，noteId: {}", dto.getId(), e);
        } finally {
            // 确保清理 ThreadLocal
            NoteHandler.clearExecutedHandlers();
        }

        return Result.failure("发布笔记失败");
    }

    /**
     * 查询笔记：缓存 -> 互斥锁 -> DB，含空值占位（防穿透）与随机 TTL（防雪崩）
     */
    private Note getNoteWithCache(Long noteId) {
        String cacheKey = RedisConstant.NOTE_DETAIL_CACHE + noteId;
        String lockKey = RedisConstant.NOTE_DETAIL_CACHE + "lock:" + noteId;

        // 1.读缓存（空值占位说明笔记不存在，直接抛业务异常防穿透）
        Object cached = redisTemplate.opsForHash().get(cacheKey, "note");
        if (cached != null) {
            return checkAndCast(cached);
        }

        // 2.互斥锁，锁值为随机 UUID
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查
                cached = redisTemplate.opsForHash().get(cacheKey, "note");
                if (cached != null) {
                    return checkAndCast(cached);
                }
                Note dbNote = noteMapper.selectById(noteId);
                if (dbNote == null) {
                    // 写入空值占位 60 秒，防止缓存穿透
                    redisTemplate.opsForHash().put(cacheKey, "note", EMPTY_PLACEHOLDER);
                    redisTemplate.expire(cacheKey, EMPTY_TTL_SECONDS, TimeUnit.SECONDS);
                    throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
                }
                // 正常缓存 TTL 300 + 随机 300 秒，防止缓存雪崩
                redisTemplate.opsForHash().put(cacheKey, "note", dbNote);
                redisTemplate.expire(cacheKey, CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt((int) CACHE_TTL_RANDOM), TimeUnit.SECONDS);
                return dbNote;
            } finally {
                // Lua 脚本比对锁值后删除，防止误删其他线程的锁
                unlock(lockKey, lockValue);
            }
        }

        // 3.未抢到锁：最多等 1 秒、每 50ms 重试读缓存
        int waited = 0;
        while (waited < 1000) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待缓存加载时线程被中断", e);
            }
            waited += 50;
            cached = redisTemplate.opsForHash().get(cacheKey, "note");
            if (cached != null) {
                return checkAndCast(cached);
            }
        }

        // 4.等待超时：直接查库兜底，结果写回缓存（不打爆 DB）
        Note dbNote = noteMapper.selectById(noteId);
        if (dbNote == null) {
            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
        }
        try {
            redisTemplate.opsForHash().put(cacheKey, "note", dbNote);
            redisTemplate.expire(cacheKey, CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt((int) CACHE_TTL_RANDOM), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("写回笔记缓存失败 noteId={}", noteId, e);
        }
        return dbNote;
    }

    /**
     * 缓存值解析：空字符串为空值占位（笔记不存在），否则强转为 Note
     */
    private Note checkAndCast(Object cached) {
        if (cached instanceof String) {
            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
        }
        return (Note) cached;
    }

    /**
     * 释放分布式锁：Lua 脚本比对锁值后删除
     */
    private void unlock(String lockKey, String lockValue) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);
            redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);
        } catch (Exception e) {
            log.error("释放缓存互斥锁失败 lockKey={}", lockKey, e);
        }
    }

    /**
     * 获取评论数，Feign 调用失败降级为 0
     */
    private Integer getCommentCountSafe(Long noteId) {
        try {
            Result<Integer> result = commentClient.getCommentCount(noteId);
            if (result != null && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.error("获取评论数失败，降级为 0 noteId={}", noteId, e);
        }
        return 0;
    }
}
