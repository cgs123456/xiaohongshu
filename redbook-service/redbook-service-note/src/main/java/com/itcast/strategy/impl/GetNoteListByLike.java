package com.itcast.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.component.UserBatchLoader;
import com.itcast.context.UserContext;
import com.itcast.mapper.LikeMapper;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Like;
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
public class GetNoteListByLike implements GetNotesStrategy {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserBatchLoader userBatchLoader;

    @Autowired
    private LikeMapper likeMapper;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.BY_LIKE;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        // 1.获取用户点赞记录（按点赞时间倒序 + 分页，此前全量拉取）
        Integer userId = UserContext.getUserId();
        int page = context.getPage() == null || context.getPage() < 1 ? 1 : context.getPage();
        int size = context.getPageSize() == null || context.getPageSize() < 1 ? 20 : Math.min(context.getPageSize(), 100);
        LambdaQueryWrapper<Like> queryWrapper
                = new LambdaQueryWrapper<Like>().eq(Like::getUserId, userId)
                .orderByDesc(Like::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + (long) (page - 1) * size);
        List<Like> likeList = likeMapper.selectList(queryWrapper);
        if (likeList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 2.一次性批量查询笔记，消除 N+1
        List<Long> noteIds = likeList.stream().map(Like::getNoteId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (noteIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        Map<Long, Note> noteMap = noteMapper.selectBatchIds(noteIds).stream()
                .collect(Collectors.toMap(Note::getId, n -> n, (a, b) -> a));
        // 3.批量查询用户
        List<Integer> userIds = noteMap.values().stream().map(Note::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = userBatchLoader.batchGetUsers(userIds);
        // 4.组装 VO，笔记不存在的跳过该条，绝不让整个列表 500
        List<NoteVo> noteVoList = new ArrayList<>(likeList.size());
        for (Like like : likeList) {
            Note note = noteMap.get(like.getNoteId());
            if (note == null) {
                continue;
            }
            NoteVo noteVo = new NoteVo();
            noteVo.setNote(note);
            noteVo.setUser(userMap.get(note.getUserId()));
            noteVoList.add(noteVo);
        }
        return Result.success(noteVoList);
    }

}
