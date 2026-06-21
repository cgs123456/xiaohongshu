package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import com.itcast.model.pojo.NoteEs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(10)
@Slf4j
public class SaveNoteToEsHandler extends NoteHandler {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        try {
            NoteEs noteEs = new NoteEs();
            BeanUtils.copyProperties(noteDto, noteEs);
            elasticsearchOperations.save(noteEs);
        } catch (Exception e) {
            return;
        }
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：从 ES 删除已保存的笔记
        if (noteDto.getId() != null) {
            try {
                NoteEs noteEs = new NoteEs();
                noteEs.setId(Math.toIntExact(noteDto.getId()));
                elasticsearchOperations.delete(noteEs);
                log.info("补偿操作：已从 ES 删除笔记，noteId: {}", noteDto.getId());
            } catch (Exception e) {
                log.error("补偿操作失败：从 ES 删除笔记 noteId: {} 时出错", noteDto.getId(), e);
            }
        }
    }
}
