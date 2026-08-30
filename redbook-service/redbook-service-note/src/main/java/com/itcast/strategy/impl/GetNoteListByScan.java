package com.itcast.strategy.impl;

import com.itcast.client.UserClient;
import com.itcast.component.UserBatchLoader;
import com.itcast.context.UserContext;
import com.itcast.mapper.NoteScanMapper;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.User;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.strategy.GetNotesStrategy;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GetNoteListByScan implements GetNotesStrategy {

    @Autowired
    private NoteScanMapper noteScanMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private UserBatchLoader userBatchLoader;

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
        if (noteList == null || noteList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 3.批量查询用户，消除 N+1
        List<Integer> userIds = noteList.stream().map(Note::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = userBatchLoader.batchGetUsers(userIds);
        // 4.组装 VO 列表
        List<NoteVo> noteVoList = new ArrayList<>(noteList.size());
        for (Note note : noteList) {
            NoteVo noteVo = new NoteVo();
            noteVo.setNote(note);
            noteVo.setUser(userMap.get(note.getUserId()));
            noteVoList.add(noteVo);
        }
        return Result.success(noteVoList);
    }

}
