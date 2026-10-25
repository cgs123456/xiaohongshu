-- ============================================
-- 订单服务 rb_order 库
-- 对齐实体：com.itcast.model.pojo.Order / OrderAttribute / OrderItem / Payment
-- ============================================
USE rb_order;

-- 订单主表：一单支持多商品（明细见 rb_order_item），
-- 冗余收货信息与金额拆解（总价/优惠/实付），支付时间用于状态机与对账
CREATE TABLE IF NOT EXISTS rb_order (
    id             BIGINT        NOT NULL COMMENT '主键（雪花id）',
    user_id        INT           DEFAULT NULL COMMENT '订单归属人ID',
    status         INT           DEFAULT 0 COMMENT '状态：0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    total_price    DECIMAL(10,2) DEFAULT 0.00 COMMENT '商品总价（下单时快照）',
    discount_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额',
    final_price    DECIMAL(10,2) DEFAULT 0.00 COMMENT '实付金额 = 总价 - 优惠',
    coupon_id      BIGINT        DEFAULT NULL COMMENT '使用的优惠券id（与 rb_coupon 主键同为 BIGINT）',
    receiver_name  VARCHAR(50)   DEFAULT '' COMMENT '收货人姓名',
    receiver_phone VARCHAR(20)   DEFAULT '' COMMENT '收货人手机号',
    address        VARCHAR(300)  DEFAULT '' COMMENT '收货地址',
    pay_time       DATETIME      DEFAULT NULL COMMENT '支付时间',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    -- 兼容旧单商品下单路径（BuyModal 立即购买已改走结算链路，历史数据仍保留这两列）
    product_id     INT           DEFAULT NULL COMMENT '（旧单）产品id',
    quantity       INT           DEFAULT 1 COMMENT '（旧单）商品数量',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 订单明细：商品名称/图片/单价为下单时快照，商品后续改价不影响历史订单
CREATE TABLE IF NOT EXISTS rb_order_item (
    id            BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id      BIGINT        NOT NULL COMMENT '订单id',
    product_id    INT           NOT NULL COMMENT '商品id',
    product_name  VARCHAR(100)  DEFAULT '' COMMENT '商品名称快照',
    product_image VARCHAR(500)  DEFAULT '' COMMENT '商品图片快照',
    unit_price    DECIMAL(10,2) DEFAULT 0.00 COMMENT '成交单价快照',
    quantity      INT           DEFAULT 1 COMMENT '数量',
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- 订单属性表（旧单下单时选择的商品规格，新结算链路规格随明细扩展字段存储）
CREATE TABLE IF NOT EXISTS rb_order_attribute (
    id       INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id BIGINT       NOT NULL COMMENT '订单id',
    label    VARCHAR(100) DEFAULT '' COMMENT '属性标签',
    `value`  VARCHAR(200) DEFAULT '' COMMENT '属性值',
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单属性表';

-- 支付流水表：模拟支付落账，供对账与退款依据
CREATE TABLE IF NOT EXISTS rb_payment (
    id         BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id   BIGINT        NOT NULL COMMENT '订单id',
    user_id    INT           NOT NULL COMMENT '支付人',
    amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
    pay_method VARCHAR(20)   DEFAULT 'MOCK' COMMENT '支付方式（当前为模拟支付）',
    status     INT           DEFAULT 1 COMMENT '状态：1-成功 0-失败',
    pay_time   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '支付时间',
    KEY idx_order_id (order_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';

-- 订单死信记录表：save.order 死信队列消费落库，供人工/定时任务补偿（此前死信队列只声明无消费者，消息静默丢失）
CREATE TABLE IF NOT EXISTS rb_order_dead_message (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    message_id  VARCHAR(64) DEFAULT NULL COMMENT '消息ID（生产者UUID）',
    payload     TEXT        COMMENT '消息体 JSON',
    error       VARCHAR(1000) DEFAULT '' COMMENT '最后一次失败原因',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单死信记录';

-- 种子数据：两条测试订单（雪花风格假 id），并带规格属性
INSERT IGNORE INTO rb_order (id, product_id, quantity, coupon_id, final_price, user_id, status) VALUES
(2000000000000000001, 1, 1, NULL, 219.00, 1, 3),
(2000000000000000002, 7, 1, 1001, 879.00, 2, 0);

INSERT IGNORE INTO rb_order_attribute (id, order_id, label, `value`) VALUES
(1, 2000000000000000001, '颜色', '小辣椒'),
(2, 2000000000000000001, '规格', '3.5g 正装'),
(3, 2000000000000000002, '颜色', '白色'),
(4, 2000000000000000002, '版本', '标准版');
