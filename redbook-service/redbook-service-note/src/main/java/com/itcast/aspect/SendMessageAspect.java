package com.itcast.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itcast.annotation.SendMessage;
import com.itcast.config.KafkaConfig;
import com.itcast.enums.LogType;
import com.itcast.model.pojo.BehaviorMessage;
import com.itcast.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.Date;

@Component
@Aspect
@Slf4j
public class SendMessageAspect {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Pointcut("execution(* com.itcast.service.impl.*.*(..)) && @annotation(com.itcast.annotation.SendMessage)")
    public void sendMessage(){}

    @AfterReturning("sendMessage()")
    public void afterReturning(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Long noteId = (Long) args[0];
        Integer userId = UserContext.getUserId();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogType type = method.getAnnotation(SendMessage.class).type();

        BehaviorMessage behaviorMessage = null;

        switch (type) {
            case SCAN:
                behaviorMessage = BehaviorMessage.builder()
                        .userId(userId)
                        .noteId(noteId)
                        .viewTime(new Date())
                        .leaveTime(new Date())
                        .logType(LogType.SCAN)
                        .build();
                break;
            case LIKE:
                behaviorMessage = BehaviorMessage.builder()
                        .userId(userId)
                        .noteId(noteId)
                        .likeTime(new Date())
                        .logType(LogType.LIKE)
                        .build();
                break;
        }

        if (behaviorMessage == null) {
            return;
        }
        // lambda 只能捕获事实最终变量，拷贝一份供异步发送使用
        final BehaviorMessage messageToSend = behaviorMessage;
        try {
            log.info("【用户行为日志】用户[id={}]对笔记[id={}]执行了{}操作", userId, noteId, type);
            // 若方法处于事务中（如 like 带 @Transactional），延后到事务提交后再发 Kafka，
            // 避免事务回滚后仍产生脏行为日志；无事务时直接发送
            Runnable sendTask = () -> {
                try {
                    kafkaTemplate.send(KafkaConfig.USER_BEHAVIOR_LOG_TOPIC, objectMapper.writeValueAsString(messageToSend));
                } catch (JsonProcessingException e) {
                    log.error("序列化用户行为日志失败 noteId:{} userId:{}", noteId, userId, e);
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendTask.run();
                    }
                });
            } else {
                sendTask.run();
            }
        } catch (Exception e) {
            log.error("发送用户行为日志失败 noteId:{} userId:{}", noteId, userId, e);
        }
    }
}
