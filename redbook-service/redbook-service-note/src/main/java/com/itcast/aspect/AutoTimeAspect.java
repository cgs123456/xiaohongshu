package com.itcast.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 自动时间填充：实体 time 字段已迁移 LocalDateTime（DB 列 DATETIME），
 * 直接注入当前时刻，秒级精度保证同日内容可按时间排序
 */
@Aspect
@Component
@Slf4j
public class AutoTimeAspect {

    @Pointcut("@annotation(com.itcast.annotation.AutoTime)")
    public void autoTime() {}

    @Before("autoTime()")
    public void before(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Object entity = args[0];
        try {
            Method setTime = entity.getClass().getDeclaredMethod("setTime", java.time.LocalDateTime.class);
            setTime.invoke(entity, java.time.LocalDateTime.now());
        } catch (Exception e) {
            log.error("自动填充时间失败", e);
        }
    }
}
