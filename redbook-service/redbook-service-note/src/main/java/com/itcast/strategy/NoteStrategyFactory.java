package com.itcast.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔记查询策略工厂
 * 自动管理所有策略实现，根据策略类型获取对应的策略
 */
@Component
public class NoteStrategyFactory {
    
    @Autowired
    private List<GetNotesStrategy> strategies;
    
    private final Map<NoteStrategyType, GetNotesStrategy> strategyMap = new HashMap<>();
    
    /**
     * 初始化策略映射
     */
    @PostConstruct
    public void init() {
        for (GetNotesStrategy strategy : strategies) {
            strategyMap.put(strategy.getStrategyType(), strategy);
        }
    }
    
    /**
     * 根据策略类型获取策略
     * @param type 策略类型
     * @return 策略实现
     */
    public GetNotesStrategy getStrategy(NoteStrategyType type) {
        GetNotesStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("未找到策略类型: " + type);
        }
        return strategy;
    }
}

