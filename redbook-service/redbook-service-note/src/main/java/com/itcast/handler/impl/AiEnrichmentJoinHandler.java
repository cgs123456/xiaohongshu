package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.dto.NoteDto;
import com.itcast.model.pojo.Note;
import com.itcast.service.impl.AiEnrichmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI 增强结果汇合 Handler（责任链 @Order(11)，SaveNoteToEsHandler 之前）。
 *
 * 对 LaunchHandler 发射的三个异步任务做有界等待（总计上限 8 秒）：
 * - 按时完成：填充 dto.type / summary / aiTags，随主链路落库与 ES 同步；
 * - 超时/失败：不阻塞发布（沿旧降级语义），任务完成后再后台补写 DB 与 ES。
 */
@Component
@Order(11)
@Slf4j
public class AiEnrichmentJoinHandler extends NoteHandler {

    /** 单个 future 的最长等待 */
    private static final long JOIN_TIMEOUT_SECONDS = 8;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        joinType(noteDto);
        joinSummary(noteDto);
        // embedding future 不在这里等——SaveNoteToEsHandler 自行 join，避免重复等待
    }

    private void joinType(NoteDto dto) {
        CompletableFuture<String> future = dto.getTypeFuture();
        if (future == null) {
            return;
        }
        try {
            String type = future.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (type != null) {
                dto.setType(type);
                log.info("AI 分类完成 noteId={} type={}", dto.getId(), type);
            }
        } catch (TimeoutException te) {
            log.warn("AI 分类超时，转后台补写 noteId={}", dto.getId());
            scheduleTypeBackfill(dto, future);
        } catch (Exception e) {
            log.warn("AI 分类失败，降级为无类型 noteId={}", dto.getId(), e);
        }
    }

    private void joinSummary(NoteDto dto) {
        CompletableFuture<AiEnrichmentService.SummaryResult> future = dto.getSummaryFuture();
        if (future == null) {
            return;
        }
        try {
            AiEnrichmentService.SummaryResult result = future.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            applySummary(dto, result);
        } catch (TimeoutException te) {
            log.warn("AI 摘要超时，转后台补写 noteId={}", dto.getId());
            future.whenComplete((result, err) -> {
                if (err != null || result == null) {
                    return;
                }
                updateEsFields(dto.getId(), summaryEsPatch(result));
            });
        } catch (Exception e) {
            log.warn("AI 摘要失败，降级为无摘要 noteId={}", dto.getId(), e);
        }
    }

    private void applySummary(NoteDto dto, AiEnrichmentService.SummaryResult result) {
        if (result == null) {
            return;
        }
        if (result.getSummary() != null && !result.getSummary().trim().isEmpty()) {
            dto.setSummary(result.getSummary().trim());
        }
        if (result.getTags() != null && !result.getTags().isEmpty()) {
            dto.setAiTags(result.getTags());
        }
    }

    /**
     * 分类超时的后台补写：LLM 返回后更新 rb_note.type 与 ES 文档的 type 字段
     */
    private void scheduleTypeBackfill(NoteDto dto, CompletableFuture<String> future) {
        future.whenComplete((type, err) -> {
            if (err != null || type == null || dto.getId() == null) {
                return;
            }
            try {
                Note patch = new Note();
                patch.setId(dto.getId());
                patch.setType(type);
                noteMapper.updateById(patch);
                Map<String, Object> doc = new HashMap<>();
                doc.put("type", type);
                updateEsFields(dto.getId(), doc);
                log.info("AI 分类后台补写完成 noteId={} type={}", dto.getId(), type);
            } catch (Exception e) {
                log.error("AI 分类后台补写失败 noteId={}", dto.getId(), e);
            }
        });
    }

    private Map<String, Object> summaryEsPatch(AiEnrichmentService.SummaryResult result) {
        Map<String, Object> doc = new HashMap<>();
        if (result.getSummary() != null && !result.getSummary().trim().isEmpty()) {
            doc.put("summary", result.getSummary().trim());
        }
        if (result.getTags() != null && !result.getTags().isEmpty()) {
            doc.put("aiTags", result.getTags());
        }
        return doc;
    }

    /** ES 笔记索引名（与 NoteEs @Document 一致） */
    private static final String NOTE_INDEX = "rb_note";

    private void updateEsFields(Long noteId, Map<String, Object> doc) {
        if (noteId == null || doc.isEmpty()) {
            return;
        }
        try {
            UpdateQuery query = UpdateQuery.builder(String.valueOf(noteId))
                    .withDocument(org.springframework.data.elasticsearch.core.document.Document.from(doc))
                    .build();
            elasticsearchOperations.update(query, IndexCoordinates.of(NOTE_INDEX));
        } catch (Exception e) {
            log.warn("ES 字段补写失败 noteId={} fields={}", noteId, doc.keySet(), e);
        }
    }
}
