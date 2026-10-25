-- ============================================
-- 搜索服务 rb_search 库
-- 对齐实体：com.itcast.model.pojo.History
-- ============================================
USE rb_search;

-- 对齐 redbook-service-search 的 History 实体（@TableName("rb_history")）
-- 实体只有 history / user_id 两个字段（无主键），user_id 建普通索引
CREATE TABLE IF NOT EXISTS rb_history (
    id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    history VARCHAR(200) DEFAULT '' COMMENT '记录（实体字段名即 history）',
    user_id INT          NOT NULL COMMENT '用户id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_history (user_id, history),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索历史表';

-- 种子数据：测试用户的搜索历史
INSERT IGNORE INTO rb_history (history, user_id) VALUES
('口红推荐', 1),
('周末露营', 1),
('机械键盘', 2),
('奶茶', 2);
