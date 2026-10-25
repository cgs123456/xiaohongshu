package com.itcast.service;

import com.itcast.model.dto.NoteDto;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyType;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

public interface NoteService {
    Result<NoteVo> getNote(Long noteId) throws ParseException;

    Result<List<NoteVo>> getNotes(NoteStrategyType strategyType, NoteStrategyContext context);

    Result<Void> postNote(NoteDto dto) throws IOException, InterruptedException;

    /** 待人工审核笔记列表（管理端点用） */
    Result<java.util.List<com.itcast.model.pojo.Note>> getPendingNotes(Integer page, Integer pageSize);

    /** 审核处置：approve=true 放行（清空 moderation_status），false 拒绝（删除笔记） */
    Result<Void> reviewNote(Long noteId, boolean approve);

    /** 全量重建 ES 笔记索引（新环境种子数据/索引丢失后使用，管理端点） */
    Result<Integer> reindexToEs();
}
