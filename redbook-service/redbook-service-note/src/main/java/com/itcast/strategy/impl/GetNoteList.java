package com.itcast.strategy.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itcast.client.UserClient;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Note;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.strategy.GetNotesStrategy;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyType;
import com.itcast.util.BloomFilterUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GetNoteList implements GetNotesStrategy {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.DEFAULT;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        // 1.构造分页
        Page<Note> ipage = new Page<>(context.getPage(), context.getPageSize());
        // 2.根据分页查询笔记
        Page<Note> notePage = noteMapper.selectPage(ipage, null);
        if (notePage == null) {
            return Result.success(new ArrayList<>());
        }
        // 3.获取记录
        List<Note> noteList = notePage.getRecords();
        if (noteList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 4.布隆过滤器过滤
        Integer userId = UserContext.getUserId();
        List<NoteVo> noteVoList = noteList.stream()
                .filter(note -> !bloomFilterUtil.mightContain(RedisConstant.USER_BLOOM_FILTER + userId, note.getId().toString()))
                .map(note -> {
                    NoteVo noteVo = new NoteVo();
                    noteVo.setNote(note);
                    noteVo.setUser(userClient.getUserById(note.getUserId()).getData());
                    return noteVo;
                })
                .collect(Collectors.toList());
        return Result.success(noteVoList);
    }
}
