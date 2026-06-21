package com.itcast.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itcast.model.pojo.TimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Slf4j
public class WriteTimeAspect {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Pointcut("execution(* com.itcast.service.impl.*.*(..))")
    public void writeTime() {}

    @Around("writeTime()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取方法信息
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String className = signature.getDeclaringTypeName();
        String methodName = signature.getMethod().getName();

        // 执行方法
        Object result = pjp.proceed();

        long endTime = System.currentTimeMillis();

        // 执行时间
        long processTime = endTime - startTime;

        // 发送接口执行时间日志到kafka
        TimeMessage timeMessage
                = TimeMessage.builder()
                .className(className)
                .methodName(methodName)
                .processTime(processTime)
                .build();
        log.info("类：{}，方法：{} => 执行时间为{}ms", className, methodName, processTime);
        kafkaTemplate.send("interface-time-log", objectMapper.writeValueAsString(timeMessage));

        return result;
    }
}
