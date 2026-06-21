package com.itcast.strategy;

import lombok.Builder;
import lombok.Data;

/**
 * 笔记查询策略上下文，用于传递策略执行所需的参数
 */
@Data
@Builder
public class NoteStrategyContext {
    /**
     * 页码
     */
    private Integer page;
    
    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 经度
     */
    private String longitude;
    
    /**
     * 纬度
     */
    private String latitude;
}

