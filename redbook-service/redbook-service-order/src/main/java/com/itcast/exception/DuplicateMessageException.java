package com.itcast.exception;

/**
 * 幂等重复消息专用异常：消息已处理过或正在处理中。
 * 消费者捕获后直接 ACK 丢弃，与业务失败（需重试/进死信）严格区分，
 * 此前两者共用 IllegalStateException 导致业务失败被静默吞掉。
 */
public class DuplicateMessageException extends RuntimeException {

    public DuplicateMessageException(String message) {
        super(message);
    }
}
