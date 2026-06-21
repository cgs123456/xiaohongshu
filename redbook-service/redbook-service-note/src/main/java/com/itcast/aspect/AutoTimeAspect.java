package com.itcast.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 自动时间填充
 */
@Aspect
@Component
@Slf4j
public class AutoTimeAspect {

    /**
     * 定义切面
     */
    @Pointcut("@annotation(com.itcast.annotation.AutoTime)")
    public void autoTime() {}

    /**
     * 前置通知
     */
    @Before("autoTime()")
    public void before(JoinPoint joinPoint) {
        log.info("开始自动填充时间");
        // 1.获取方法中的参数
        Object[] args = joinPoint.getArgs();
        // 2.获取第一个参数
        Object entity = args[0];
        // 3.使用反射获取setter方法
        try {
            Method setTime = entity.getClass().getDeclaredMethod("setTime", String.class);
            // 4.设置值
            setTime.invoke(entity, new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        } catch (Exception e) {
            log.error("发生异常",e);
        }
    }
}
