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

import java.util.Set;

@Component
@Aspect
@Slf4j
public class WriteTimeAspect {

    /**
     * 写操作方法名白名单：仅对这些写方法上报执行时间，纯读的列表/详情查询不再刷 Kafka
     */
    private static final Set<String> WRITE_METHODS = Set.of(
            "postNote", "like", "collection", "attention", "postComment", "buyProduct");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Pointcut("execution(* com.itcast.service.impl.*.*(..))")
    public void writeTime() {}

    @Around("writeTime()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 获取方法信息
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String className = signature.getDeclaringTypeName();
        String methodName = signature.getMethod().getName();

        // 非写操作（白名单外）直接放行，不上报 Kafka
        if (!WRITE_METHODS.contains(methodName)) {
            return pjp.proceed();
        }

        long startTime = System.currentTimeMillis();

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
