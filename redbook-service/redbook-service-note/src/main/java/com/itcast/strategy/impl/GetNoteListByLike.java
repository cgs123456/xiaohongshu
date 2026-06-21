package com.itcast.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.client.UserClient;
import com.itcast.constant.ExceptionConstant;
import com.itcast.context.UserContext;
import com.itcast.exception.NoteNoExistException;
import com.itcast.mapper.LikeMapper;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Like;
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
public class GetNoteListByLike implements GetNotesStrategy {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private LikeMapper likeMapper;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.BY_LIKE;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        // 1.获取用户收藏
        Integer userId = UserContext.getUserId();
        LambdaQueryWrapper<Like> queryWrapper
                = new LambdaQueryWrapper<Like>().eq(Like::getUserId, userId);
        List<Like> likeList = likeMapper.selectList(queryWrapper);
        if (likeList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 2.设置vo
        List<NoteVo> noteVoList = likeList.stream().map(like -> {
            Note note = noteMapper.selectById(like.getNoteId());
            if (note == null) {
                throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
            }
            NoteVo noteVo = new NoteVo();
            noteVo.setNote(note);
            noteVo.setUser(userClient.getUserById(note.getUserId()).getData());
            return noteVo;
        }).collect(Collectors.toList());
        return Result.success(noteVoList);
    }
}
