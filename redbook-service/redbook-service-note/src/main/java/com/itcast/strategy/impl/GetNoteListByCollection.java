package com.itcast.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.client.UserClient;
import com.itcast.constant.ExceptionConstant;
import com.itcast.context.UserContext;
import com.itcast.exception.NoteNoExistException;
import com.itcast.mapper.CollectionMapper;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Collection;
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
public class GetNoteListByCollection implements GetNotesStrategy {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private CollectionMapper collectionMapper;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.BY_COLLECTION;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        // 1.获取用户收藏
        Integer userId = UserContext.getUserId();
        LambdaQueryWrapper<Collection> queryWrapper
                = new LambdaQueryWrapper<Collection>().eq(Collection::getUserId, userId);
        List<Collection> collectionList = collectionMapper.selectList(queryWrapper);
        if (collectionList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 2.设置vo
        List<NoteVo> noteVoList = collectionList.stream().map(collection -> {
            Note note = noteMapper.selectById(collection.getNoteId());
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
