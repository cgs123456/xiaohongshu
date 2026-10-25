package com.itcast.handle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itcast.exception.AttriIsNullException;
import com.itcast.exception.CodeErrorException;
import com.itcast.exception.FileIsNullException;
import com.itcast.exception.NoteNoExistException;
import com.itcast.exception.UserNoExistException;
import com.itcast.model.pojo.ExceptionMessage;
import com.itcast.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常处理
 */
@ControllerAdvice
@ResponseBody
@Slf4j
public class GlobalExceptionHandle {


    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理业务异常（自定义异常）
     */
    @ExceptionHandler({NoteNoExistException.class, UserNoExistException.class, CodeErrorException.class, 
                       FileIsNullException.class, AttriIsNullException.class})
    public Result<String> businessException(Exception e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.failure(e.getMessage());
    }

    /**
     * 处理系统异常（不暴露内部信息）
     */
    @ExceptionHandler(value = Exception.class)
    public Result<String> exception(Exception e) {
        try {
            StackTraceElement[] stackTrace = e.getStackTrace();
            // JVM 参数 -XX:+OmitStackTraceInFastThrow 热路径异常会丢栈，数组可能为空导致处理器自身抛异常
            StackTraceElement firstStack = stackTrace.length > 0 ? stackTrace[0] : null;
            if (firstStack == null) {
                log.error("系统异常（无堆栈信息）：", e);
                return Result.failure("系统繁忙，请稍后重试");
            }
            ExceptionMessage exceptionMessage
                    = ExceptionMessage.builder()
                    .className(firstStack.getClassName())
                    .methodName(firstStack.getMethodName())
                    .line(firstStack.getLineNumber())
                    .fileName(firstStack.getFileName())
                    .build();
            log.error("异常发生在: {}.{}({}:{})",
                    firstStack.getClassName(), firstStack.getMethodName(), firstStack.getFileName(), firstStack.getLineNumber());
            log.error("异常信息: {}", e.getMessage());
            // 行为/异常日志 Kafka 管道已下线（无消费者、broker 未部署）；结构化信息进应用日志供采集
            log.error("结构化异常: {}", objectMapper.writeValueAsString(exceptionMessage));
        } catch (JsonProcessingException jsonProcessingException) {
            log.error("记录日志失败:{}", jsonProcessingException.getMessage());
        }
        return Result.failure("服务器内部错误");
    }
}
