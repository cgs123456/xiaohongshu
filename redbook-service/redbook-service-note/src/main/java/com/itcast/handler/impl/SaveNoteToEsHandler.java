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
@Order(12)
@Slf4j
public class SaveNoteToEsHandler extends NoteHandler {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        NoteEs noteEs = new NoteEs();
        BeanUtils.copyProperties(noteDto, noteEs);
        // Long 主键到 String 显式转换（BeanUtils 类型不兼容会静默跳过，导致 ES 自动生成非数字 _id，
        // 搜索侧按 Long 反解失败）
        if (noteDto.getId() != null) {
            noteEs.setId(String.valueOf(noteDto.getId()));
        }
        // Note.time 已是 LocalDateTime，ES 文档模型保持字符串（BeanUtils 类型不兼容会静默跳过）
        if (noteDto.getTime() != null) {
            noteEs.setTime(noteDto.getTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        // Embedding 向量：LaunchHandler 已并行发射，这里有界等待（8s）；超时降级为无向量写入，
        // 任务完成后再后台补写 ES（此前同步调用最长阻塞发布 15s）
        try {
            java.util.concurrent.CompletableFuture<float[]> embeddingFuture = noteDto.getEmbeddingFuture();
            if (embeddingFuture != null) {
                try {
                    float[] embedding = embeddingFuture.get(8, java.util.concurrent.TimeUnit.SECONDS);
                    if (embedding != null) {
                        noteEs.setEmbedding(embedding);
                    }
                } catch (java.util.concurrent.TimeoutException te) {
                    log.warn("embedding 超时，转后台补写 noteId={}", noteDto.getId());
                    embeddingFuture.whenComplete((vector, err) -> {
                        if (err == null && vector != null && noteDto.getId() != null) {
                            try {
                                org.springframework.data.elasticsearch.core.query.UpdateQuery query =
                                        org.springframework.data.elasticsearch.core.query.UpdateQuery
                                                .builder(String.valueOf(noteDto.getId()))
                                                .withDocument(org.springframework.data.elasticsearch.core.document.Document
                                                        .from(java.util.Map.of("embedding", vector)))
                                                .build();
                                elasticsearchOperations.update(query,
                                        org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.of("rb_note"));
                                log.info("embedding 后台补写完成 noteId={}", noteDto.getId());
                            } catch (Exception e) {
                                log.warn("embedding 后台补写失败 noteId={}", noteDto.getId(), e);
                            }
                        }
                    });
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
