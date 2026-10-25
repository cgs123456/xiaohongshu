package com.itcast.model.dto;

import com.itcast.model.pojo.Note;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoteDto extends Note {
    private MultipartFile file;
    private List<Integer> topicList;
    /**
     * 本次发布新建的话题 ID（补偿时删除）。
     * 显式集合取代此前 Map<topicId,oldHot> 以 null 值区分"新建/存量"的隐式约定——
     * 存量话题 hot 为 NULL 时会被误判为新建而遭到删除。
     * 由 GetTopicHandler 写入、compensate 读取；随请求级 NoteDto 生命周期销毁，无线程残留问题
     */
    private java.util.Set<Integer> createdTopicIds;
    /** 本次发布热度被自增的存量话题 ID（补偿时原子回退，不回写旧值以免吞掉并发自增） */
    private java.util.Set<Integer> incrementedTopicIds;
    /** 内容审核状态：null=未审核/放行，"PENDING_REVIEW"=待人工审核，"BLOCKED"=已拦截 */
    private String moderationStatus;
    /** AI 生成的 50 字摘要（transient，不存数据库，仅同步到 ES 用于搜索增强） */
    private String summary;
    /** AI 生成的 3 个话题标签（transient，不存数据库，仅同步到 ES 用于搜索增强） */
    private List<String> aiTags;
    /** AI 分类异步任务（AiEnrichmentLaunchHandler 发射、JoinHandler 有界等待，随请求生命周期销毁） */
    private transient java.util.concurrent.CompletableFuture<String> typeFuture;
    /** AI 摘要异步任务（同上） */
    private transient java.util.concurrent.CompletableFuture<com.itcast.service.impl.AiEnrichmentService.SummaryResult> summaryFuture;
    /** Embedding 向量异步任务（同上） */
    private transient java.util.concurrent.CompletableFuture<float[]> embeddingFuture;
}
