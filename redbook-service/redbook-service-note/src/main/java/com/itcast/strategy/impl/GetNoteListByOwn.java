package com.itcast.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.client.UserClient;
import com.itcast.component.UserBatchLoader;
import com.itcast.context.UserContext;
import com.itcast.mapper.NoteMapper;
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
public class GetNoteListByOwn implements GetNotesStrategy {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private UserBatchLoader userBatchLoader;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.BY_OWN;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        Integer userId = UserContext.getUserId();
        // 1.支持分页，按发布时间倒序
        int page = page(context);
        int pageSize = pageSize(context);
        int offset = (page - 1) * pageSize;
        LambdaQueryWrapper<Note> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Note::getUserId, userId)
                .orderByDesc(Note::getTime)
                .last("LIMIT " + offset + "," + pageSize);
        List<Note> noteList = noteMapper.selectList(queryWrapper);
        if (noteList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 2.批量查询用户，消除 N+1
        List<Integer> userIds = noteList.stream().map(Note::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = userBatchLoader.batchGetUsers(userIds);
        // 3.组装 VO
        List<NoteVo> noteVoList = new ArrayList<>(noteList.size());
        for (Note note : noteList) {
            NoteVo noteVo = new NoteVo();
            noteVo.setNote(note);
            noteVo.setUser(userMap.get(note.getUserId()));
            noteVoList.add(noteVo);
        }
        return Result.success(noteVoList);
    }


    private int page(NoteStrategyContext context) {
        if (context == null || context.getPage() == null || context.getPage() < 1) {
            return 1;
        }
        return context.getPage();
    }

    private int pageSize(NoteStrategyContext context) {
        int size = (context == null || context.getPageSize() == null) ? 20 : context.getPageSize();
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
