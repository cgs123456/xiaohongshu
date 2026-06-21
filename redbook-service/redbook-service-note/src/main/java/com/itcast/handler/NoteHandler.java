package com.itcast.handler;

import com.itcast.model.dto.NoteDto;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
public abstract class NoteHandler {

    /**
     * 已执行的 Handler 列表，用于补偿
     */
    private static final ThreadLocal<List<NoteHandler>> executedHandlers = ThreadLocal.withInitial(ArrayList::new);

    /**
     * 处理责任链抽象类
     * @param noteDto noteDto
     */
    public abstract void handle(NoteDto noteDto) throws IOException, InterruptedException;

    /**
     * 补偿操作（回滚）
     * 当后续步骤失败时，需要执行补偿操作来撤销已执行的操作
     * @param noteDto noteDto
     */
    public void compensate(NoteDto noteDto) {
        // 默认实现为空，子类可以重写
        log.debug("Handler [{}] 执行补偿操作（默认无操作）", this.getClass().getSimpleName());
    }

    /**
     * 执行责任链，支持 Saga 补偿机制
     * @param noteDto noteDto
     */
    public void handleWithCompensation(NoteDto noteDto, Iterator<NoteHandler> remainingHandlers) throws IOException, InterruptedException {
        try {
            // 执行当前 Handler
            this.handle(noteDto);

            // 记录已执行的 Handler
            executedHandlers.get().add(this);

            // 继续执行下一个 Handler
            if (remainingHandlers != null && remainingHandlers.hasNext()) {
                NoteHandler nextHandler = remainingHandlers.next();
                nextHandler.handleWithCompensation(noteDto, remainingHandlers);
            }
        } catch (Exception e) {
            log.error("Handler [{}] 执行失败，开始执行补偿操作", this.getClass().getSimpleName(), e);

            // 执行补偿：按相反顺序回滚已执行的 Handler
            compensateExecutedHandlers(noteDto);

            // 抛出异常，交由外层捕获
            throw e;
        }
    }
    
    /**
     * 初始化补偿上下文（在责任链开始执行前调用）
     */
    public static void initCompensationContext() {
        executedHandlers.get().clear();
    }

    /**
     * 补偿已执行的 Handler（按相反顺序）
     * @param noteDto noteDto
     */
    private void compensateExecutedHandlers(NoteDto noteDto) {
        List<NoteHandler> executed = executedHandlers.get();
        // 按相反顺序执行补偿
        for (int i = executed.size() - 1; i >= 0; i--) {
            NoteHandler handler = executed.get(i);
            try {
                log.info("执行 Handler [{}] 的补偿操作", handler.getClass().getSimpleName());
                handler.compensate(noteDto);
            } catch (Exception e) {
                log.error("Handler [{}] 补偿操作失败", handler.getClass().getSimpleName(), e);
                // 补偿失败也继续执行其他补偿，确保尽可能回滚
            }
        }
        // 清空已执行的 Handler 列表
        executed.clear();
    }

    /**
     * 清理 ThreadLocal
     */
    public static void clearExecutedHandlers() {
        executedHandlers.remove();
    }
}
