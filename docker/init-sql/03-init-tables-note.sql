-- ============================================
-- 强制本文件以 utf8mb4 会话执行：docker mysql 客户端在 C locale 下
-- 默认 latin1 会话，曾把种子数据双重编码入库（乱码根因）
SET NAMES utf8mb4;
-- 笔记服务 rb_note 库
-- 对齐实体：com.itcast.model.pojo.Note / Like / Collection / NoteBrowse / Topic / NoteTopic
-- ============================================
USE rb_note;

-- 对齐 redbook-service-note 的 Note 实体（@TableName("rb_note")）
-- @TableId 未指定 type，默认 ASSIGN_ID（雪花 id），主键为 BIGINT 且非自增
CREATE TABLE IF NOT EXISTS rb_note (
    id         BIGINT       NOT NULL COMMENT '主键（雪花id）',
    title      VARCHAR(100) DEFAULT '' COMMENT '标题',
    content    TEXT         COMMENT '内容',
    image      VARCHAR(1000) DEFAULT '' COMMENT '图片',
    time       DATETIME     DEFAULT NULL COMMENT '发布时间（实体为 LocalDateTime）',
    type       VARCHAR(50)  DEFAULT '' COMMENT '类型',
    address    VARCHAR(200) DEFAULT '' COMMENT '地址',
    longitude  DOUBLE       DEFAULT NULL COMMENT '经度',
    latitude   DOUBLE       DEFAULT NULL COMMENT '纬度',
    moderation_status VARCHAR(20) DEFAULT NULL COMMENT '审核状态：NULL-放行 PENDING_REVIEW-待人工审核（信息流暂不展示）',
    `like`     INT          DEFAULT 0 COMMENT '点赞',
    collection INT          DEFAULT 0 COMMENT '收藏',
    user_id    INT          DEFAULT NULL COMMENT '发布人id',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_time (time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记表';

-- 对齐 redbook-service-note 的 Like 实体（@TableName("rb_like")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_like (
    id          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    note_id     BIGINT      NOT NULL COMMENT '笔记id',
    own_id      INT         DEFAULT NULL COMMENT '点赞人id（实体自有字段）',
    user_id     INT         NOT NULL COMMENT '用户id',
    create_time DATETIME    DEFAULT NULL COMMENT '点赞时间（实体为 Date）',
    UNIQUE KEY uk_user_note (user_id, note_id),
    KEY idx_note_id (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记点赞表';

-- 对齐 redbook-service-note 的 Collection 实体（@TableName("rb_collection")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_collection (
    id      INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    note_id BIGINT NOT NULL COMMENT '笔记id',
    user_id INT    NOT NULL COMMENT '用户id',
    UNIQUE KEY uk_user_note (user_id, note_id),
    KEY idx_note_id (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记收藏表';

-- 对齐 redbook-service-note 的 NoteBrowse 实体（@TableName("rb_note_browse")）
-- 实体无主键字段（note_id + user_id + time 三字段），用联合唯一键代替自增主键
CREATE TABLE IF NOT EXISTS rb_note_browse (
    note_id BIGINT      NOT NULL COMMENT '笔记id',
    user_id INT         NOT NULL COMMENT '用户id',
    time    DATETIME    DEFAULT NULL COMMENT '浏览时间（实体为 LocalDateTime）',
    UNIQUE KEY uk_user_note (user_id, note_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记浏览记录表';

-- 对齐 redbook-service-note 的 Topic 实体（@TableName("rb_topic")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_topic (
    id      INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    content VARCHAR(200) DEFAULT '' COMMENT '话题内容',
    hot     INT DEFAULT 0 COMMENT '热度'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题表';

-- 对齐 redbook-service-note 的 NoteTopic 实体（@TableName("rb_note_topic")，IdType.AUTO）
CREATE TABLE IF NOT EXISTS rb_note_topic (
    id       INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    topic_id INT    NOT NULL COMMENT '话题id',
    note_id  BIGINT NOT NULL COMMENT '笔记id',
    KEY idx_note_id (note_id),
    KEY idx_topic_id (topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记-话题关联表';

-- 种子数据：测试笔记（id 用固定雪花风格的假值，避免与 ASSIGN_ID 生成的冲突概率）
INSERT IGNORE INTO rb_note (id, title, content, image, time, type, address, longitude, latitude, `like`, collection, user_id) VALUES
(1001, '分享我家的装修风格！超喜欢这个飘窗设计', '最近刚装修完，飘窗是我最满意的部分，阳光洒进来超治愈~', 'https://picsum.photos/seed/note1/600/600', '2026-07-01 10:00:00', '家居', '上海市杨浦区', 121.5221, 31.2645, 1700, 200, 1),
(1002, '三口之家的幸福时光，这样的周末太治愈了', '周末带娃去公园，记录下这些美好的瞬间', 'https://picsum.photos/seed/note2/600/600', '2026-07-03 15:30:00', '生活', '深圳市南山区', 113.9304, 22.5331, 3200, 500, 2),
(1003, '100万打造的婚礼现场！每个细节都超满意', '从策划到执行花了半年时间，终于圆满了！', 'https://picsum.photos/seed/note3/600/600', '2026-07-05 18:20:00', '婚庆', '杭州市西湖区', 120.1303, 30.2590, 5100, 800, 1),
(1004, '把白娘子送到法国庄园，这波旅拍操作绝了', '旅拍分享，中西合璧的感觉太绝了', 'https://picsum.photos/seed/note4/600/600', '2026-07-08 09:10:00', '旅行', '巴黎', 2.3522, 48.8566, 8900, 1200, 2),
(1005, '最爱拉丝奶酪，这家店的芝士真的太绝了', '探店分享，这家店的芝士披萨绝了', 'https://picsum.photos/seed/note5/600/600', '2026-07-12 12:45:00', '美食', '成都市锦江区', 104.0800, 30.6570, 2300, 300, 1),
(1006, '美妆分享｜让皮肤变好的秘密都在这里了', '坚持用了三个月，皮肤真的变好了', 'https://picsum.photos/seed/note6/600/600', '2026-07-15 20:00:00', '美妆', '北京市朝阳区', 116.4431, 39.9218, 1800, 250, 2);

-- 话题与笔记-话题关联种子
INSERT IGNORE INTO rb_topic (id, content, hot) VALUES
(1, '装修日记', 9800),
(2, '周末去哪儿', 8600),
(3, '美妆好物', 12000);

INSERT IGNORE INTO rb_note_topic (id, topic_id, note_id) VALUES
(1, 1, 1001),
(2, 2, 1002),
(3, 3, 1006),
(4, 3, 1003);

-- 点赞 / 收藏 / 浏览种子（测试用户对部分笔记的互动）
INSERT IGNORE INTO rb_like (id, note_id, own_id, user_id, create_time) VALUES
(1, 1001, 2, 2, '2026-07-02 11:00:00'),
(2, 1003, 2, 2, '2026-07-06 08:00:00'),
(3, 1002, 1, 1, '2026-07-04 14:00:00');

INSERT IGNORE INTO rb_collection (id, note_id, user_id) VALUES
(1, 1004, 1),
(2, 1001, 2);

INSERT IGNORE INTO rb_note_browse (note_id, user_id, time) VALUES
(1001, 2, '2026-07-02 10:58:00'),
(1002, 1, '2026-07-04 13:55:00'),
(1003, 2, '2026-07-06 07:50:00');
