package com.itcast.strategy;

import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;

import java.util.List;

/**
 * 笔记查询策略接口
 */
public interface GetNotesStrategy {
    /**
     * 获取策略类型
     */
    NoteStrategyType getStrategyType();
    
    /**
     * 执行查询
     * @param context 策略上下文，包含查询所需的参数
     */
    Result<List<NoteVo>> getNotes(NoteStrategyContext context);
}
