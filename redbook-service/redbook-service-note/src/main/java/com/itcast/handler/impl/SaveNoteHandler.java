package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.dto.NoteDto;
import com.itcast.model.pojo.Note;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(7)
@Slf4j
public class SaveNoteHandler extends NoteHandler {

    @Autowired
    private NoteMapper noteMapper;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        Note note = new Note();
        BeanUtils.copyProperties(noteDto, note);
        noteMapper.insert(note);
        noteDto.setId(note.getId());
        log.info("===已保存笔记，noteId: {}===", note.getId());
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：删除已保存的笔记
        if (noteDto.getId() != null) {
            try {
                noteMapper.deleteById(noteDto.getId());
                log.info("补偿操作：已删除笔记，noteId: {}", noteDto.getId());
            } catch (Exception e) {
                log.error("补偿操作失败：删除笔记 noteId: {} 时出错", noteDto.getId(), e);
            }
        }
    }
}
