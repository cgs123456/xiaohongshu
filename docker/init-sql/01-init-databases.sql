-- ============================================
-- 小红书项目数据库初始化脚本
-- 仅创建数据库，表结构与种子数据见 02~06 号脚本
-- 数据库名与各服务 application.yml 中 jdbc url 保持一致
-- ============================================

-- 用户服务数据库（redbook-service-user）
CREATE DATABASE IF NOT EXISTS rb_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 笔记服务数据库（redbook-service-note）
CREATE DATABASE IF NOT EXISTS rb_note DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 商品服务数据库（redbook-service-product）
CREATE DATABASE IF NOT EXISTS rb_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 订单服务数据库（redbook-service-order）
CREATE DATABASE IF NOT EXISTS rb_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 搜索服务数据库（redbook-service-search）
CREATE DATABASE IF NOT EXISTS rb_search DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
