package com.itcast.handler.impl;

import com.itcast.context.UserContext;
import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@Slf4j
public class SetUserIdHandler extends NoteHandler {
    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        Integer userId = UserContext.getUserId();
        noteDto.setUserId(userId);
        log.info("===已设置用户 ID: {}===", userId);
    }
}
