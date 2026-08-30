-- ============================================
-- 用户服务 rb_user 库
-- 对齐实体：com.itcast.model.pojo.User / Attention
-- ============================================
USE rb_user;

-- 对齐 redbook-service-user 的 User 实体（@TableName("rb_user")，@TableId(type = IdType.AUTO)）
-- 注意：实体无 username/password/mail/create_time/update_time 字段，故不建这些列
CREATE TABLE IF NOT EXISTS rb_user (
    id        INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    phone     VARCHAR(20)  DEFAULT '' COMMENT '手机号',
    nickname  VARCHAR(50)  DEFAULT '' COMMENT '昵称',
    image     VARCHAR(500) DEFAULT '' COMMENT '头像',
    number    BIGINT       DEFAULT NULL COMMENT '小红书号',
    sex       VARCHAR(10)  DEFAULT '' COMMENT '性别',
    birthday  VARCHAR(20)  DEFAULT '' COMMENT '生日（实体为 String）',
    address   VARCHAR(100) DEFAULT '' COMMENT '地区',
    identity  VARCHAR(50)  DEFAULT '' COMMENT '身份',
    school    VARCHAR(100) DEFAULT '' COMMENT '学校',
    signature VARCHAR(200) DEFAULT '' COMMENT '个性签名',
    time      VARCHAR(50)  DEFAULT '' COMMENT '注册时间（实体为 String）',
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_number (number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 对齐 redbook-service-user 的 Attention 实体（@TableName("rb_attention")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_attention (
    id       INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    other_id INT          NOT NULL COMMENT '被关注者id',
    own_id   INT          NOT NULL COMMENT '关注者id',
    time     VARCHAR(50)  DEFAULT '' COMMENT '关注时间（实体为 String）',
    UNIQUE KEY uk_own_other (own_id, other_id),
    KEY idx_other_id (other_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注表';

-- 种子数据：2 个测试用户（登录手机号 13800000001 / 13800000002）
INSERT IGNORE INTO rb_user (id, phone, nickname, image, number, sex, birthday, address, identity, school, signature, time) VALUES
(1, '13800000001', '小红薯阿珍', 'https://i.pravatar.cc/150?img=1', 20260000001, '女', '1998-06-15', '上海', '学生', '同济大学', '分享美好生活', '2026-01-10 12:00:00'),
(2, '13800000002', '干饭人阿强', 'https://i.pravatar.cc/150?img=2', 20260000002, '男', '1995-11-03', '深圳', '上班族', '华南理工大学', '干饭人干饭魂', '2026-01-12 09:30:00');

-- 关注关系种子：用户 1 关注用户 2
INSERT IGNORE INTO rb_attention (id, other_id, own_id, time) VALUES
(1, 2, 1, '2026-02-01 10:00:00');
