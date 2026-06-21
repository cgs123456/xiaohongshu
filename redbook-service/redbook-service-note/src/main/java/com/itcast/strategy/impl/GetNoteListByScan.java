package com.itcast.strategy.impl;

import com.itcast.client.UserClient;
import com.itcast.context.UserContext;
import com.itcast.mapper.NoteMapper;
import com.itcast.mapper.NoteScanMapper;
import com.itcast.model.pojo.Note;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.strategy.GetNotesStrategy;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GetNoteListByScan implements GetNotesStrategy {

    @Autowired
    private NoteScanMapper noteScanMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserClient userClient;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.BY_SCAN;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        // 1.获取登录用户
        Integer userId = UserContext.getUserId();
        // 2.通过连接查询直接获取笔记列表
        List<Note> noteList = noteScanMapper.selectNotesByUserId(userId);
        if (noteList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 3.设置vo列表
        List<NoteVo> noteVoList = noteList.stream().map(note -> {
            NoteVo noteVo = new NoteVo();
            noteVo.setNote(note);
            noteVo.setUser(userClient.getUserById(note.getUserId()).getData());
            return noteVo;
        }).collect(Collectors.toList());
        return Result.success(noteVoList);
    }
}
