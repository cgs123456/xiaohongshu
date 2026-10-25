package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import com.itcast.service.EmbeddingService;
import com.itcast.service.impl.AiEnrichmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * AI 增强发射 Handler（责任链 @Order(6)，SaveNoteHandler 之前）。
 *
 * 把笔记分类 / 摘要+标签 / Embedding 三个 LLM 调用并行发射到专用线程池，
 * 由 AiEnrichmentJoinHandler（@Order(11)）在落库后有界等待结果——
 * 发布主链路的 LLM 等待时间从"三次串行之和（最坏 45s+）"降为"并行最大值（上限 8s 等待）"。
 * 发射失败不影响链路继续（JoinHandler 按 future 缺失降级）。
 */
@Component
@Order(6)
@Slf4j
public class AiEnrichmentLaunchHandler extends NoteHandler {

    @Autowired
    private AiEnrichmentService aiEnrichmentService;

    /** embedding.enabled=false 时 common 中不注册该 Bean，必须可选注入 */
    @Autowired(required = false)
    private EmbeddingService embeddingService;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        String title = noteDto.getTitle();
        String content = noteDto.getContent();
        boolean textBlank = (title == null || title.trim().isEmpty())
                && (content == null || content.trim().isEmpty());

        try {
            // 分类：无文本无意义，跳过
            if (!textBlank) {
                noteDto.setTypeFuture(aiEnrichmentService.generateType(title, content));
            }
            // 摘要 + 标签
            if (!textBlank) {
                noteDto.setSummaryFuture(aiEnrichmentService.generateSummary(title, content));
            }
            // Embedding 向量（未启用时服务 Bean 不存在，跳过）
            if (!textBlank && embeddingService != null && embeddingService.isAvailable()) {
                String text = ((title == null ? "" : title) + " " + (content == null ? "" : content)).trim();
                noteDto.setEmbeddingFuture(embeddingService.embedAsync(text));
            }
        } catch (Exception e) {
            // 发射失败降级为无 AI 增强，不阻塞发布
            log.warn("AI 增强任务发射失败，降级为无增强 noteId={}", noteDto.getId(), e);
        }
    }
}
