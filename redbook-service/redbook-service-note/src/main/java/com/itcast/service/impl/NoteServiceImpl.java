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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

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
        Note note;
        // 1.查询redis中是否存在缓存
        Note cacheNote = (Note) redisTemplate.opsForHash().get(RedisConstant.NOTE_DETAIL_CACHE + noteId, "note");

        // 2.如果缓存不存在，则
        if (cacheNote == null) {
            // 使用 Redis 分布式锁防止缓存击穿
            String lockKey = RedisConstant.NOTE_DETAIL_CACHE + "lock:" + noteId;
            Boolean locked = Boolean.FALSE;
            try {
                locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(locked)) {
                    // 双重检查
                    Note doubleCheck = (Note) redisTemplate.opsForHash().get(RedisConstant.NOTE_DETAIL_CACHE + noteId, "note");
                    if (doubleCheck != null) {
                        note = doubleCheck;
                    } else {
                        // 查询数据库
                        note = noteMapper.selectById(noteId);
                        if (note == null) {
                            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
                        }
                        // 缓存到redis并设置有效时间
                        redisTemplate.opsForHash().put(RedisConstant.NOTE_DETAIL_CACHE + noteId, "note", note);
                        redisTemplate.expire(RedisConstant.NOTE_DETAIL_CACHE + noteId, 5, TimeUnit.MINUTES);
                    }
                } else {
                    // 等待其他线程加载完成，使用循环重试避免栈溢出
                    int retryCount = 0;
                    int maxRetries = 20; // 最多重试20次，总计等待1秒
                    Note retryNote = null;
                    while (retryCount < maxRetries) {
                        Thread.sleep(50);
                        // 检查缓存是否已加载
                        retryNote = (Note) redisTemplate.opsForHash().get(RedisConstant.NOTE_DETAIL_CACHE + noteId, "note");
                        if (retryNote != null) {
                            break;
                        }
                        retryCount++;
                    }
                    if (retryNote == null) {
                        // 重试后仍未获取到缓存，直接查询数据库
                        retryNote = noteMapper.selectById(noteId);
                        if (retryNote == null) {
                            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
                        }
                    }
                    note = retryNote;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("线程被中断", e);
                throw new RuntimeException("获取缓存时线程被中断", e);
            } finally {
                // 只在持锁时才释放锁
                if (Boolean.TRUE.equals(locked)) {
                    redisTemplate.delete(lockKey);
                }
            }
        } else {
            note = cacheNote;
        }

        // 3.获取发布笔记用户信息
        User user = userClient.getUserById(note.getUserId()).getData();
        if (user == null) {
            throw new UserNoExistException(ExceptionConstant.USER_NO_EXIST);
        }

        // 4.处理时间字符串
        int days = DiffDayUtil.diffDays(
                new SimpleDateFormat("yyyy-MM-dd").parse(note.getTime()), new Date());
        String dealTime = DealTimeUtil.dealTime(days);
        if (StringUtils.isBlank(dealTime)) {
            dealTime = note.getTime();
        }

        // 5.设置vo
        NoteVo noteVo = new NoteVo();
        noteVo.setNote(note);
        noteVo.setDealTime(dealTime);
        noteVo.setUser(user);
        noteVo.setComment(commentClient.getCommentCount(noteId).getData());

        // 6.加入布隆过滤器
        Integer loginUserId = UserContext.getUserId();
        threadPoolTaskExecutor.execute(() -> bloomFilterUtil.add(RedisConstant.USER_BLOOM_FILTER + loginUserId, noteId.toString()));

       // 7.记录用户访问笔记
       try {
           NoteBrowse noteBrowse = new NoteBrowse();
           noteBrowse.setNoteId(noteId);
           noteBrowse.setUserId(loginUserId);
           noteScanMapper.insert(noteBrowse);
       } catch (Exception e) {
           log.error("用户已经访问过，不需要再次插入数据库", e);
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
            log.error("笔记发布失败，已执行补偿操作，noteId: {}", dto.getId(), e);
        } finally {
            // 确保清理 ThreadLocal
            NoteHandler.clearExecutedHandlers();
        }

        return Result.failure("发布笔记失败");
    }
}

