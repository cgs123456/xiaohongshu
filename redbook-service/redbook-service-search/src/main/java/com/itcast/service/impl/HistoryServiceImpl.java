package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.mapper.HistoryMapper;
import com.itcast.result.Result;
import com.itcast.model.pojo.History;
import com.itcast.service.HistoryService;
import com.itcast.context.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryServiceImpl implements HistoryService {

    @Autowired
    private HistoryMapper historyMapper;

    @Override
    public Result<List<History>> getHistoryList() {
        Integer userId = UserContext.getUserId();
        LambdaQueryWrapper<History> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(History::getUserId, userId);
        List<History> historyList = historyMapper.selectList(queryWrapper);
        return Result.success(historyList);
    }

    @Override
    public Result<Void> deleteHistory() {
        Integer userId = UserContext.getUserId();
        LambdaQueryWrapper<History> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(History::getUserId, userId);
        historyMapper.delete(queryWrapper);
        return Result.success(null);
    }
}
