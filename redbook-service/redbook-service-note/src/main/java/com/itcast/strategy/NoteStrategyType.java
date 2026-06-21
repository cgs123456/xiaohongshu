package com.itcast.strategy;

/**
 * 笔记查询策略类型枚举
 */
public enum NoteStrategyType {
    /**
     * 普通列表查询
     */
    DEFAULT,
    /**
     * 根据位置查询
     */
    BY_LOCATION,
    /**
     * 查询自己的笔记
     */
    BY_OWN,
    /**
     * 根据浏览记录查询
     */
    BY_SCAN,
    /**
     * 根据关注查询
     */
    BY_ATTENTION,
    /**
     * 根据收藏查询
     */
    BY_COLLECTION,
    /**
     * 根据点赞查询
     */
    BY_LIKE
}

