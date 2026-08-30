package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.constant.ExceptionConstant;
import com.itcast.constant.MqConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.exception.CodeErrorException;
import com.itcast.mapper.UserMapper;
import com.itcast.result.Result;
import com.itcast.service.LoginService;
import com.itcast.model.dto.LoginDto;
import com.itcast.model.pojo.User;
import com.itcast.util.CodeUtil;
import com.itcast.util.JwtUtil;
import com.itcast.util.NumberUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LoginServiceImpl implements LoginService {

    /**
     * 验证码有效期（秒）
     */
    private static final long CODE_TTL_SECONDS = 300L;
    /**
     * 同一手机号发送间隔（秒）
     */
    private static final long SEND_INTERVAL_SECONDS = 60L;
    /**
     * 每日发送次数上限
     */
    private static final long DAILY_SEND_LIMIT = 10L;
    /**
     * 校验失败锁定时长（分钟）
     */
    private static final long VERIFY_LOCK_MINUTES = 10L;
    /**
     * 最大校验失败次数
     */
    private static final int MAX_VERIFY_FAIL_COUNT = 5;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result<String> send(String phone) {
        // 手机号格式校验
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return Result.failure("手机号格式不正确");
        }
        String codeKey = RedisConstant.PHONE_CODE.concat(phone);
        String intervalKey = RedisConstant.PHONE_CODE.concat("interval:").concat(phone);
        String dailyKey = RedisConstant.PHONE_CODE.concat("daily:").concat(phone).concat(":").concat(LocalDate.now().toString());

        // 1.发送间隔限制：同一手机号 60 秒内只能发一次
        Boolean first = redisTemplate.opsForValue().setIfAbsent(
                intervalKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            return Result.failure("发送过于频繁，请稍后再试");
        }

        // 2.每日限 10 次（INCR + 当天过期）
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            redisTemplate.expire(dailyKey, 1, TimeUnit.DAYS);
        }
        if (dailyCount != null && dailyCount > DAILY_SEND_LIMIT) {
            return Result.failure("今日发送次数已达上限");
        }

        // 3.生成 6 位验证码并保存到 redis（短信通道未接入，开发联调时从日志取码）
        String code = CodeUtil.generateCode(6);
        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("验证码已生成 phone={} code={}（仅供联调，生产环境必须接入短信通道后移除该日志）",
                phone.length() == 11 ? phone.substring(0, 3) + "****" + phone.substring(7) : phone, code);

        // 4.返回结果（验证码不再回显给前端）
        return Result.success("验证码已发送");
    }

    @Override
    public Result<String> verify(LoginDto loginDto) {
        String phone = loginDto.getPhone();
        String codeKey = RedisConstant.PHONE_CODE.concat(phone);
        String failCountKey = RedisConstant.PHONE_CODE.concat("fail:").concat(phone);

        // 1.比对前检查失败计数：5 次校验失败锁定 10 分钟
        String failCount = redisTemplate.opsForValue().get(failCountKey);
        if (failCount != null && Integer.parseInt(failCount) >= MAX_VERIFY_FAIL_COUNT) {
            throw new CodeErrorException("校验失败次数过多，请" + VERIFY_LOCK_MINUTES + "分钟后再试");
        }

        // 2.原子获取并删除验证码（getAndDelete），防止并发重复使用
        String cacheCode = redisTemplate.opsForValue().getAndDelete(codeKey);
        boolean codeOk = cacheCode != null && loginDto.getCode() != null && cacheCode.equals(loginDto.getCode());
        if (!codeOk) {
            // 3.失败计数（INCR + EXPIRE），达到上限后锁定
            Long fails = redisTemplate.opsForValue().increment(failCountKey);
            if (fails != null && fails == 1L) {
                redisTemplate.expire(failCountKey, VERIFY_LOCK_MINUTES, TimeUnit.MINUTES);
            }
            throw new CodeErrorException(ExceptionConstant.CODE_ERROR);
        }

        // 4.校验成功，清空失败计数
        redisTemplate.delete(failCountKey);

        // 5.获取用户id
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, loginDto.getPhone());
        User dbUser = userMapper.selectOne(queryWrapper);
        Integer userId;
        // 6.判断用户是否存在
        if (dbUser == null) {
            // 6.1 注册
            User user = new User();
            user.setPhone(loginDto.getPhone());
            user.setNickname("普通用户");
            user.setImage("https://redbook512.oss-cn-beijing.aliyuncs.com/default.png");
            // 6.2 生成小红书号
            Long number = NumberUtil.getNumber();
            user.setNumber(number);
            userMapper.insert(user);
            userId = user.getId();
        } else {
            userId = dbUser.getId();
        }
        // 7.生成token
        String token = JwtUtil.createToken(userId);

        // 向mq中发送消息
        rabbitTemplate.convertAndSend(MqConstant.MESSAGE_NOTICE_EXCHANGE, MqConstant.LOGIN_KEY, userId);

        // 8.返回token
        return Result.success(token);
    }
}
