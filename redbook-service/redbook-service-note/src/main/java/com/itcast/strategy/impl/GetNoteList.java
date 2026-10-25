package com.itcast.strategy.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itcast.component.UserBatchLoader;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.User;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.strategy.GetNotesStrategy;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyType;
import com.itcast.util.BloomFilterUtil;
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
public class GetNoteList implements GetNotesStrategy {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserBatchLoader userBatchLoader;

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.DEFAULT;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        // 1.构造分页与查询条件（type 为首页分类筛选；按 id 倒序保证信息流稳定有序）
        Page<Note> ipage = new Page<>(context.getPage(), context.getPageSize());
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Note> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (context.getType() != null && !context.getType().isBlank()) {
            wrapper.eq(Note::getType, context.getType());
        }
        // 待人工审核的笔记不进信息流（此前 PENDING_REVIEW 只写不读，审核标记形同虚设）
        wrapper.and(w -> w.isNull(Note::getModerationStatus)
                .or().ne(Note::getModerationStatus, "PENDING_REVIEW"));
        wrapper.orderByDesc(Note::getId);
        // 2.根据分页查询笔记
        Page<Note> notePage = noteMapper.selectPage(ipage, wrapper);
        if (notePage == null) {
            return Result.success(new ArrayList<>());
        }
        // 3.获取记录
        List<Note> noteList = notePage.getRecords();
        if (noteList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 4.布隆过滤器过滤（未登录 userId 为 null 时跳过，避免 key 变成 "...null" 且全量过滤为空）；
        // 整页一次批量判定（此前逐条 EVAL，20 条/页 = 20 次往返）
        Integer userId = UserContext.getUserId();
        List<Note> filteredList = noteList;
        if (userId != null) {
            java.util.List<String> ids = noteList.stream()
                    .map(n -> String.valueOf(n.getId())).collect(Collectors.toList());
            java.util.Set<String> seen = bloomFilterUtil.mightContainBatch(
                    RedisConstant.USER_BLOOM_FILTER + userId, ids);
            filteredList = noteList.stream()
                    .filter(note -> !seen.contains(String.valueOf(note.getId())))
                    .collect(Collectors.toList());
        }
        if (filteredList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 5.批量查询用户，消除 N+1
        List<Integer> userIds = filteredList.stream().map(Note::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = userBatchLoader.batchGetUsers(userIds);
        // 6.组装 VO
        List<NoteVo> noteVoList = new ArrayList<>(filteredList.size());
        for (Note note : filteredList) {
            NoteVo noteVo = new NoteVo();
            noteVo.setNote(note);
            noteVo.setUser(userMap.get(note.getUserId()));
            noteVoList.add(noteVo);
        }
        return Result.success(noteVoList);
    }

}
