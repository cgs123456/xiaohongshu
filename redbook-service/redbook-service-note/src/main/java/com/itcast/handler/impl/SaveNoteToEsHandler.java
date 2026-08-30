package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import com.itcast.model.pojo.NoteEs;
import com.itcast.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(12)
@Slf4j
public class SaveNoteToEsHandler extends NoteHandler {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private EmbeddingService embeddingService;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        NoteEs noteEs = new NoteEs();
        BeanUtils.copyProperties(noteDto, noteEs);

        // 生成 embedding 向量（失败降级为不写入向量，不阻塞发布）
        try {
            if (embeddingService.isAvailable()) {
                String text = (noteDto.getTitle() == null ? "" : noteDto.getTitle()) + " "
                        + (noteDto.getContent() == null ? "" : noteDto.getContent());
                float[] embedding = embeddingService.embed(text.trim());
                if (embedding != null) {
                    noteEs.setEmbedding(embedding);
                }
            }
        } catch (Exception e) {
            log.warn("生成 embedding 失败，降级为无向量写入 noteId={}", noteDto.getId(), e);
        }

        // 写入 ES：纯增强步骤，允许失败不阻断发布（记录日志后继续链）
        try {
            elasticsearchOperations.save(noteEs);
        } catch (Exception e) {
            log.error("保存笔记到 ES 失败，不阻断发布 noteId={}", noteDto.getId(), e);
        }
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：从 ES 删除已保存的笔记
        if (noteDto.getId() != null) {
            try {
                NoteEs noteEs = new NoteEs();
                noteEs.setId(String.valueOf(noteDto.getId()));
                elasticsearchOperations.delete(noteEs);
                log.info("补偿操作：已从 ES 删除笔记，noteId: {}", noteDto.getId());
            } catch (Exception e) {
                log.error("补偿操作失败：从 ES 删除笔记 noteId: {} 时出错", noteDto.getId(), e);
            }
        }
    }
}
