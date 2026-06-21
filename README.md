# RedBook - 仿小红书社交电商平台

基于 Spring Cloud 微服务架构的社交电商全栈项目，融合笔记分享、商品交易、即时通讯等核心功能。

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.3.5 | 基础框架 |
| Spring Cloud | 2023.0.3 | 微服务框架 |
| Spring Cloud Alibaba | 2023.0.1.2 | Nacos 注册/配置中心 |
| Spring Cloud Gateway | 2023.0.3 | API 网关 |
| MyBatis Plus | 3.5.7 | ORM 框架（spring-boot3-starter） |
| MySQL | 8.0 | 关系型数据库 |
| Redis | 7.2 | 缓存 / 分布式锁 |
| Redisson | 3.37.0 | 分布式锁实现 |
| Elasticsearch | 8.15.3 | 全文搜索引擎（Spring Data Elasticsearch） |
| MongoDB | 7.0 | 商品属性存储 |
| RabbitMQ | 3.13 | 消息队列 |
| Netty | 4.1.115 | WebSocket 实时通讯 |
| JWT (auth0) | 4.4.0 | 身份认证 |
| Aliyun OSS | 3.17.4 | 对象存储 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.13 | 前端框架 |
| TypeScript | 5.7.3 | 类型安全 |
| Vite | 6.4.3 | 构建工具 |
| Vue Router | 4.5.0 | 路由管理 |
| Axios | 1.7.9 | HTTP 客户端 |
| DOMPurify | 3.2.3 | XSS 防护 |
| Iconify | 5.0.1 | 图标库 |

## 项目结构

```
my-redbook/
├── redbook-common/          # 公共模块（工具类、常量、通用配置）
├── redbook-gateway/         # API 网关（端口 10010）
├── redbook-service/         # 微服务集群
│   ├── redbook-service-user/       # 用户服务 - 登录注册、关注、个人信息
│   ├── redbook-service-note/       # 笔记服务 - 发布、点赞、收藏
│   ├── redbook-service-product/    # 商品服务 - 商品管理、购物车、优惠券
│   ├── redbook-service-order/      # 订单服务 - 下单、订单管理
│   ├── redbook-service-comment/    # 评论服务 - 发表评论、评论列表
│   ├── redbook-service-search/     # 搜索服务 - ES 全文搜索、热搜、历史
│   ├── redbook-service-message/    # 消息服务 - Netty WebSocket 实时通知
│   └── redbook-service-ai/         # AI 服务 - 智能购物助手
└── redbook-frontend/        # 前端项目（Vue 3 + TypeScript）
```

## 核心功能

### 用户模块
- 手机号 + 验证码登录
- JWT 身份认证（Token 过期自动清除）
- 用户信息编辑、头像上传（Aliyun OSS）
- 关注 / 取消关注

### 笔记模块
- 笔记发布（图文、话题标签）
- 笔记浏览（多种策略：推荐、关注、收藏、点赞、浏览、同城）
- 点赞 / 收藏（数据库原子操作，防并发）
- 评论系统
- 敏感词过滤、布隆过滤器

### 商品模块
- 商品列表 / 详情
- 购物车管理（增删改查、库存校验）
- 优惠券系统
- 立即购买（Redis 库存预热 + Lua 脚本原子扣减）

### 订单模块
- 下单流程（Saga 分布式事务）
- 异步下单（RabbitMQ 消息队列）
- 订单管理

### 搜索模块
- Elasticsearch 全文搜索（Spring Data Elasticsearch + NativeQuery）
- 搜索历史记录
- 热搜排行

### 消息模块
- Netty WebSocket 实时推送
- 点赞 / 收藏 / 关注 / 评论通知
- 心跳保活、断线重连

### AI 模块
- 智能购物助手对话

## 架构设计亮点

### 安全
- JWT 密钥从环境变量读取，杜绝硬编码
- 所有数据库 / 中间件密码使用 `${ENV_VAR:default}` 占位
- DOMPurify 防御 XSS 攻击
- 网关统一鉴权 + 路径匹配防护

### 并发控制
- Redis 分布式锁解决缓存击穿
- 数据库原子操作（`SET SQL = like + 1`）解决点赞 / 收藏计数竞态
- Redis Lua 脚本实现库存原子扣减
- 分布式锁（Redisson）保障购买流程安全

### 缓存策略
- 笔记详情逻辑过期方案，防止缓存雪崩
- 热点数据 Redis 缓存 + 分布式锁防击穿
- 库存预热到 Redis，异步落库

### 消息驱动
- RabbitMQ 异步解耦：下单、通知、行为记录
- Netty WebSocket 长连接实时推送
- 自定义注解 `@SendMessage` + AOP 切面自动发送消息

### 设计模式
- 策略模式：笔记列表多种获取策略
- 责任链模式：笔记发布处理链（上传、过滤、存储）
- Saga 模式：分布式事务编排

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Docker Desktop | 最新版（推荐） |
| MySQL | 8.0 |
| Redis | 7.0+ |
| RabbitMQ | 3.13+ |
| MongoDB | 7.0+ |
| Elasticsearch | 8.15.x |
| Nacos | 2.3+ |

## 快速开始

### 1. 启动基础设施（Docker Compose）

```bash
cd my-redbook
docker compose up -d
```

将启动以下服务：

| 容器名 | 服务 | 端口 | 默认账号 |
|--------|------|------|----------|
| redbook-nacos | Nacos | 8848, 9848 | nacos/nacos |
| redbook-mysql | MySQL | 3307（外部）→ 3306 | root/root123 |
| redbook-redis | Redis | 6379 | 密码: redis123 |
| redbook-rabbitmq | RabbitMQ | 5672, 15672 | admin/rabbitmq123 |
| redbook-mongodb | MongoDB | 27017 | root/mongo123 |
| redbook-elasticsearch | Elasticsearch | 9200, 9300 | 无安全认证 |
| redbook-zookeeper | ZooKeeper | 2181 | - |
| redbook-kafka | Kafka | 9092 | - |

### 2. 环境变量配置

```bash
# 数据库
export DB_PASSWORD=your_db_password
export DB_USERNAME=root

# Redis
export REDIS_PASSWORD=your_redis_password

# RabbitMQ
export RABBITMQ_PASSWORD=your_rabbitmq_password

# MongoDB
export MONGO_PASSWORD=your_mongo_password

# JWT 密钥（建议 64 位以上随机字符串）
export JWT_SECRET_KEY=your_secure_jwt_secret_key

# Aliyun OSS
export OSS_ACCESS_KEY_ID=your_access_key_id
export OSS_ACCESS_KEY_SECRET=your_access_key_secret
export OSS_BUCKET_NAME=your_bucket_name
```

### 3. 启动后端服务

```bash
# 编译项目
cd my-redbook
mvn clean package -DskipTests

# 依次启动各服务（顺序建议）
# 1. 网关
java -jar redbook-gateway/target/redbook-gateway-1.0-SNAPSHOT.jar

# 2. 用户服务
java -jar redbook-service/redbook-service-user/target/redbook-service-user-1.0-SNAPSHOT.jar

# 3. 笔记服务
java -jar redbook-service/redbook-service-note/target/redbook-service-note-1.0-SNAPSHOT.jar

# 4. 商品服务
java -jar redbook-service/redbook-service-product/target/redbook-service-product-1.0-SNAPSHOT.jar

# 5. 订单服务
java -jar redbook-service/redbook-service-order/target/redbook-service-order-1.0-SNAPSHOT.jar

# 6. 评论服务
java -jar redbook-service/redbook-service-comment/target/redbook-service-comment-1.0-SNAPSHOT.jar

# 7. 搜索服务
java -jar redbook-service/redbook-service-search/target/redbook-service-search-1.0-SNAPSHOT.jar

# 8. 消息服务（Netty WebSocket）
java -jar redbook-service/redbook-service-message/target/redbook-service-message-1.0-SNAPSHOT.jar

# 9. AI 服务
java -jar redbook-service/redbook-service-ai/target/redbook-service-ai-1.0-SNAPSHOT.jar
```

### 4. 启动前端

```bash
cd redbook-frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，通过 Vite 代理转发到网关 `http://localhost:10010`。

## 服务端口

| 服务 | 端口 |
|------|------|
| API 网关 | 10010 |
| 用户服务 | 8080 |
| 笔记服务 | 8085 |
| 商品服务 | 8095 |
| 订单服务 | 8105 |
| 评论服务 | 8100 |
| 搜索服务 | 8090 |
| 消息服务 (Netty) | 8889 |
| AI 服务 | 8091 |
| 前端 (Vite) | 5173 |

## API 网关路由

所有请求通过网关统一入口，按路径前缀分发：

| 路径前缀 | 目标服务 |
|----------|----------|
| `/user/**` | 用户服务 |
| `/note/**` | 笔记服务 |
| `/product/**` | 商品服务 |
| `/order/**` | 订单服务 |
| `/comment/**` | 评论服务 |
| `/search/**` | 搜索服务 |
| `/ai/**` | AI 服务 |
| `/ws/**` | 消息服务 (WebSocket) |

