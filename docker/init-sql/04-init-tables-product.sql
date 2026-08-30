-- ============================================
-- 商品服务 rb_product 库
-- 对齐实体：com.itcast.model.pojo.Product / Shop / Cart / Coupon / UserCoupon / ProductBrowse
-- ============================================
USE rb_product;

-- 对齐 redbook-service-product 的 Shop 实体（@TableName("rb_shop")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_shop (
    id      INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name    VARCHAR(100) DEFAULT '' COMMENT '店铺名称',
    image   VARCHAR(500) DEFAULT '' COMMENT '头像',
    time    VARCHAR(50)  DEFAULT '' COMMENT '成立时间（实体为 String）',
    fans    INT DEFAULT 0 COMMENT '粉丝',
    sales   INT DEFAULT 0 COMMENT '销量',
    user_id INT DEFAULT NULL COMMENT '用户id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- 对齐 redbook-service-product 的 Product 实体（@TableName("rb_product")，IdType.AUTO）
-- type 为中文分类：美妆 / 服饰 / 食品 / 数码 / 家居（前端按这些分类过滤）
CREATE TABLE IF NOT EXISTS rb_product (
    id          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name        VARCHAR(100)  DEFAULT '' COMMENT '商品名称',
    type        VARCHAR(20)   DEFAULT '' COMMENT '商品类型（中文分类）',
    description VARCHAR(500)  DEFAULT '' COMMENT '商品描述',
    brand       VARCHAR(50)   DEFAULT '' COMMENT '商品品牌',
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品价格',
    image       VARCHAR(500)  DEFAULT '' COMMENT '商品图片',
    time        VARCHAR(50)   DEFAULT '' COMMENT '发布时间（实体为 String）',
    sales       INT DEFAULT 0 COMMENT '销量',
    user_id     INT DEFAULT NULL COMMENT '用户id',
    shop_id     INT DEFAULT NULL COMMENT '店铺id',
    stock       INT DEFAULT 0 COMMENT '库存',
    KEY idx_shop_id (shop_id),
    KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- 对齐 redbook-service-product 的 Cart 实体（@TableName("rb_cart")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_cart (
    id            INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id       INT           NOT NULL COMMENT '用户ID',
    product_id    INT           NOT NULL COMMENT '商品ID',
    product_name  VARCHAR(100)  DEFAULT '' COMMENT '商品名称',
    product_image VARCHAR(500)  DEFAULT '' COMMENT '商品图片',
    product_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '商品价格',
    quantity      INT DEFAULT 1 COMMENT '数量',
    create_time   DATETIME DEFAULT NULL COMMENT '创建时间（实体为 LocalDateTime）',
    update_time   DATETIME DEFAULT NULL COMMENT '更新时间（实体为 LocalDateTime）',
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- 对齐 redbook-service-product 的 Coupon 实体（@TableName("rb_coupon")）
-- @TableId(type = IdType.ASSIGN_ID)，主键为 BIGINT 且非自增
-- 冲突说明：任务要求 KEY(status)，但 Coupon 实体没有 status 字段；
-- 为满足索引要求补充 status 列（DEFAULT 0，MyBatis-Plus 插入不写该列时取默认值，不影响实体映射）
CREATE TABLE IF NOT EXISTS rb_coupon (
    id         BIGINT        NOT NULL COMMENT '主键（雪花id）',
    name       VARCHAR(100)  DEFAULT '' COMMENT '券名',
    type       VARCHAR(20)   DEFAULT '' COMMENT '类型',
    discount   DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额',
    start_time DATETIME      DEFAULT NULL COMMENT '生效时间（实体为 Date）',
    end_time   DATETIME      DEFAULT NULL COMMENT '失效时间（实体为 Date）',
    stock      INT DEFAULT 0 COMMENT '库存',
    status     INT DEFAULT 0 COMMENT '状态（实体无此字段，仅用于索引）：0-可用 1-下架',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券表';

-- 对齐 redbook-service-product 的 UserCoupon 实体（@TableName("rb_user_coupon")）
-- 实体只有 user_id / coupon_id 两个字段（无主键），用联合唯一键防止重复发券
-- 冲突说明：任务要求种子数据 status=0（未使用），但 UserCoupon 实体没有 status 字段；
-- 为满足要求补充 status 列（DEFAULT 0），MyBatis-Plus 插入不写该列时取默认值，不影响实体映射
CREATE TABLE IF NOT EXISTS rb_user_coupon (
    user_id   INT NOT NULL COMMENT '用户id',
    coupon_id INT NOT NULL COMMENT '优惠券id',
    status    INT DEFAULT 0 COMMENT '使用状态（实体无此字段，仅用于种子数据）：0-未使用 1-已使用',
    UNIQUE KEY uk_user_coupon (user_id, coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-优惠券关联表';

-- 对齐 redbook-service-product 的 ProductBrowse 实体（@TableName("rb_product_browse")）
-- 实体只有 product_id / user_id 两个字段（无主键），用联合唯一键去重
CREATE TABLE IF NOT EXISTS rb_product_browse (
    product_id INT NOT NULL COMMENT '产品id',
    user_id    INT NOT NULL COMMENT '用户id',
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品浏览记录表';

-- 种子数据：店铺（归属用户 1）
INSERT IGNORE INTO rb_shop (id, name, image, time, fans, sales, user_id) VALUES
(1, '阿珍的宝藏小店', 'https://picsum.photos/seed/shop1/200/200', '2025-03-01 10:00:00', 5200, 18000, 1);

-- 种子数据：10 个商品，type 使用中文分类（美妆/服饰/食品/数码/家居 各 2 个）
INSERT IGNORE INTO rb_product (id, name, type, description, brand, price, image, time, sales, user_id, shop_id, stock) VALUES
(1,  '丝绒哑光口红 #小辣椒', '美妆', '显白不拔干的经典色号，黄皮友好', '花西子', 219.00, 'https://picsum.photos/seed/product1/600/600', '2026-06-01 10:00:00', 3200, 1, 1, 200),
(2,  '水感防晒霜 SPF50+',    '美妆', '清爽不油腻，户外通勤两相宜', '安热沙', 268.00, 'https://picsum.photos/seed/product2/600/600', '2026-06-02 11:00:00', 2100, 1, 1, 300),
(3,  '复古法式碎花连衣裙',   '服饰', '收腰显瘦，度假日常都能穿', '太平鸟', 399.00, 'https://picsum.photos/seed/product3/600/600', '2026-06-03 12:00:00', 1500, 1, 1, 150),
(4,  '纯棉宽松连帽卫衣',     '服饰', '重磅纯棉，oversize 版型', '优衣库', 249.00, 'https://picsum.photos/seed/product4/600/600', '2026-06-04 13:00:00', 2800, 1, 1, 260),
(5,  '手作黑糖珍珠奶茶礼盒', '食品', '手作黑糖珍珠，冲泡即饮', '茶颜悦色', 89.00,  'https://picsum.photos/seed/product5/600/600', '2026-06-05 14:00:00', 5600, 1, 1, 500),
(6,  '每日坚果 30 日装',     '食品', '六种坚果果干科学配比，新鲜锁存', '三只松鼠', 129.00, 'https://picsum.photos/seed/product6/600/600', '2026-06-06 15:00:00', 7300, 1, 1, 800),
(7,  '真无线降噪耳机 Pro',   '数码', '40dB 深度降噪，续航 32 小时', '小米', 899.00,  'https://picsum.photos/seed/product7/600/600', '2026-06-07 16:00:00', 1900, 1, 1, 180),
(8,  '便携机械键盘 87 键',   '数码', '热插拔轴体，三模连接', '罗技', 499.00,   'https://picsum.photos/seed/product8/600/600', '2026-06-08 17:00:00', 1200, 1, 1, 220),
(9,  '北欧简约陶瓷香薰灯',   '家居', '暖光助眠，卧室氛围感拉满', '宜家', 159.00,  'https://picsum.photos/seed/product9/600/600', '2026-06-09 18:00:00', 2400, 1, 1, 350),
(10, '全棉四件套床品套件',   '家居', 'A 类母婴级面料，裸睡不扎', '水星家纺', 429.00, 'https://picsum.photos/seed/product10/600/600', '2026-06-10 19:00:00', 980, 1, 1, 120);

-- 种子数据：3 张优惠券（主键为 ASSIGN_ID，这里用固定小值占位；
-- 注意不能用超过 INT 上限的雪花值：rb_user_coupon.coupon_id / rb_order.coupon_id 按
-- Java 实体 Integer 映射为 INT，存不下 BIGINT 雪花 id）
INSERT IGNORE INTO rb_coupon (id, name, type, discount, start_time, end_time, stock, status) VALUES
(1001, '新人立减券',   '满减', 20.00,  '2026-08-01 00:00:00', '2026-12-31 23:59:59', 1000, 0),
(1002, '满 299 减 50', '满减', 50.00,  '2026-08-01 00:00:00', '2026-12-31 23:59:59', 500, 0),
(1003, '数码品类券',   '品类', 100.00, '2026-09-01 00:00:00', '2026-11-30 23:59:59', 300, 0);

-- 种子数据：把券发给测试用户（status=0 未使用；status 为补充列，实体无此字段）
INSERT IGNORE INTO rb_user_coupon (user_id, coupon_id, status) VALUES
(1, 1001, 0),
(1, 1003, 0),
(2, 1001, 0),
(2, 1002, 0);

-- 种子数据：购物车（测试用户 1 加购了口红和坚果）
INSERT IGNORE INTO rb_cart (id, user_id, product_id, product_name, product_image, product_price, quantity, create_time, update_time) VALUES
(1, 1, 1, '丝绒哑光口红 #小辣椒', 'https://picsum.photos/seed/product1/600/600', 219.00, 1, '2026-08-01 10:00:00', '2026-08-01 10:00:00'),
(2, 1, 5, '手作黑糖珍珠奶茶礼盒', 'https://picsum.photos/seed/product5/600/600', 89.00, 2, '2026-08-02 15:30:00', '2026-08-02 15:30:00');

-- 种子数据：商品浏览记录
INSERT IGNORE INTO rb_product_browse (product_id, user_id) VALUES
(1, 2),
(7, 2),
(3, 1);
