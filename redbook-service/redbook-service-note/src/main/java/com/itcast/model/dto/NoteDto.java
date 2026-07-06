package com.itcast.model.dto;

import com.itcast.model.pojo.Note;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoteDto extends Note {
    private MultipartFile file;
    private List<Integer> topicList;
    /** 内容审核状态：null=未审核/放行，"PENDING_REVIEW"=待人工审核，"BLOCKED"=已拦截 */
    private String moderationStatus;
    /** AI 生成的 50 字摘要（transient，不存数据库，仅同步到 ES 用于搜索增强） */
    private String summary;
    /** AI 生成的 3 个话题标签（transient，不存数据库，仅同步到 ES 用于搜索增强） */
    private List<String> aiTags;
}
