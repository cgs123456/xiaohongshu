-- ============================================
-- 订单服务 rb_order 库
-- 对齐实体：com.itcast.model.pojo.Order / OrderAttribute
-- ============================================
USE rb_order;

-- 对齐 redbook-service-order 的 Order 实体（@TableName("rb_order")）
-- @TableId(type = IdType.ASSIGN_ID)，主键为 BIGINT 且非自增
-- 注意：Order 实体没有 create_time 字段；任务要求 KEY(create_time)，
-- 故补充 create_time 列（DEFAULT CURRENT_TIMESTAMP，MyBatis-Plus 插入不写该列时自动填充，不影响实体映射）
CREATE TABLE IF NOT EXISTS rb_order (
    id          BIGINT        NOT NULL COMMENT '主键（雪花id）',
    product_id  INT           DEFAULT NULL COMMENT '产品id',
    quantity    INT           DEFAULT 1 COMMENT '商品数量',
    coupon_id   INT           DEFAULT NULL COMMENT '优惠券id',
    final_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '最终价格',
    user_id     INT           DEFAULT NULL COMMENT '订单归属人ID',
    status      INT           DEFAULT 0 COMMENT '状态：0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间（实体无此字段，仅用于索引）',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 对齐 redbook-service-order 的 OrderAttribute 实体（@TableName("rb_order_attribute")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_order_attribute (
    id       INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id BIGINT       NOT NULL COMMENT '订单id',
    label    VARCHAR(100) DEFAULT '' COMMENT '属性标签',
    `value`  VARCHAR(200) DEFAULT '' COMMENT '属性值'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单属性表';

-- 种子数据：两条测试订单（雪花风格假 id），并带规格属性
INSERT IGNORE INTO rb_order (id, product_id, quantity, coupon_id, final_price, user_id, status) VALUES
(2000000000000000001, 1, 1, NULL, 219.00, 1, 3),
(2000000000000000002, 7, 1, 1001, 879.00, 2, 0);

INSERT IGNORE INTO rb_order_attribute (id, order_id, label, `value`) VALUES
(1, 2000000000000000001, '颜色', '小辣椒'),
(2, 2000000000000000001, '规格', '3.5g 正装'),
(3, 2000000000000000002, '颜色', '白色'),
(4, 2000000000000000002, '版本', '标准版');
