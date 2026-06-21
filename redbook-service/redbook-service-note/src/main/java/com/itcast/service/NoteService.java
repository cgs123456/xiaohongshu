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
}
