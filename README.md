# RedBook - 仿小红书社交电商平台

基于 Spring Cloud 微服务架构的社交电商全栈项目，融合笔记分享、商品交易、即时通讯等核心功能。

## 技术栈

### 后端

| 技术                   | 版本         | 说明                                |
| -------------------- | ---------- | --------------------------------- |
| Java                 | 17         | 运行环境                              |
| Spring Boot          | 3.3.5      | 基础框架                              |
| Spring Cloud         | 2023.0.3   | 微服务框架                             |
| Spring Cloud Alibaba | 2023.0.1.2 | Nacos 注册/配置中心                     |
| Spring Cloud Gateway | 2023.0.3   | API 网关                            |
| MyBatis Plus         | 3.5.7      | ORM 框架（spring-boot3-starter）      |
| MySQL                | 8.0        | 关系型数据库                            |
| Redis                | 7.2        | 缓存 / 分布式锁                         |
| Redisson             | 3.37.0     | 分布式锁实现                            |
| Elasticsearch        | 8.15.3     | 全文搜索引擎（Spring Data Elasticsearch） |
| MongoDB              | 7.0        | 商品属性存储                            |
| RabbitMQ             | 3.13       | 消息队列                              |
| Netty                | 4.1.115    | WebSocket 实时通讯                    |
| JWT (auth0)          | 4.4.0      | 身份认证                              |
| Aliyun OSS           | 3.17.4     | 对象存储                              |

### 前端

| 技术         | 版本     | 说明       |
| ---------- | ------ | -------- |
| Vue        | 3.5.13 | 前端框架     |
| TypeScript | 5.7.3  | 类型安全     |
| Vite       | 6.4.3  | 构建工具     |
| Vue Router | 4.5.0  | 路由管理     |
| Axios      | 1.7.9  | HTTP 客户端 |
| DOMPurify  | 3.2.3  | XSS 防护   |
| Iconify    | 5.0.1  | 图标库      |

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

- 手机号 + 验证码登录（6 位码、60s 发送间隔、每日限额、原子校验防并发复用、5 次失败锁定）
- JWT 身份认证（Token 过期自动清除，密钥走环境变量）
- 用户信息编辑、头像上传（Aliyun OSS，客户端单例复用）
- 关注 / 取消关注（Redis Set + 唯一键幂等）
- 批量用户查询接口 `/user/getUserByIds`（供各服务消除 N+1）

### 笔记模块

- 笔记发布（图文、话题标签）
- 笔记浏览（多种策略：推荐、关注、收藏、点赞、浏览、同城，均支持分页 + 批量查询）
- 点赞 / 收藏（唯一键幂等 + 计数条件更新，Redis/MQ 操作事务提交后执行）
- 评论系统（MongoDB 存储，一级评论分页 + 楼中楼归组，批量组装用户信息）
- 敏感词过滤（内存缓存 + 定时刷新）、布隆过滤器（防重复推荐）

### 商品模块

- 商品列表 / 详情（支持分页）
- 购物车管理（前端完整页面：增删改查、数量校验、合计）
- 优惠券系统（核销校验持券状态 + 条件更新防重复刷券）
- 立即购买（Redis 库存预热 + Lua 脚本原子扣减，数量全链路透传）

### 订单模块

- **购物车结算下单**（多商品一单）：收货信息 + 优惠券门槛校验 + 商品快照（`/cart/checkout`），订单含明细表/金额拆解/收货地址
- 模拟支付（`/order/pay/{orderId}`，乐观状态机 + 支付流水表），订单列表含明细（`/order/getOrderList`）
- 下单流程（Saga 分布式事务）
- 异步下单（RabbitMQ 消息队列，UUID messageId 幂等 + 重试上限 + 死信队列）
- **订单超时关单**：15 分钟延迟消息（死信队列方案）自动取消待支付订单、按明细回补库存并退还优惠券；回补/退券失败进 Redis 补偿队列由定时任务重试，兜底扫描 + 订单死信落表（`rb_order_dead_message`）
- 订单管理（分页查询）

### 搜索模块

- Elasticsearch 全文搜索（Spring Data Elasticsearch + NativeQuery）
- **BM25 + dense_vector 混合检索**：ES 8.15 kNN 向量检索 + BM25 文本检索 → RRF 融合算法，搜索相关度显著提升
- 搜索历史记录
- 热搜排行

### 消息模块

- Netty WebSocket 实时推送（IdleStateHandler 心跳踢死链）
- 点赞 / 收藏 / 关注 / 评论通知
- **离线消息补发**：用户不在线时通知落 Redis（7 天），上线自动补发
- 心跳保活、断线重连（前端指数退避，最多 8 次）

### AI 模块

- 智能购物助手对话
- **购物 Agent（Function Calling）**：多轮对话编排，5 个工具调用（搜索/详情/加购/下单/优惠券），Redis 会话上下文 + Few-Shot 自学习
- **笔记内容审核 Agent**：责任链插入 LLM 多模态审核，三档置信度处理（拦截/人工复审/放行）
- **AI 笔记摘要/标签生成**：发布时 LLM 生成 50 字摘要 + 3 个话题标签，存入 ES 搜索增强

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
- 责任链模式：笔记发布处理链（12 个 Handler：上传→定位→审核→过滤→类型→话题→存储→位置→ES 同步，含 LLM 审核 + AI 摘要）
- Saga 模式：分布式事务编排 + 补偿型责任链
- Agent 编排模式：购物 Agent 多轮 Function Calling + Few-Shot 自学习

### AI 能力增强（DeepSeek + Embedding）

| 能力            | 实现位置                                  | 说明                                                                          |
| ------------- | ------------------------------------- | --------------------------------------------------------------------------- |
| 购物 Agent      | `redbook-service-product/agent/`      | 多轮对话编排（最多 5 轮），5 个工具调用（search_products/get_product_detail/add_to_cart/create_order/check_coupons），Redis 会话上下文（TTL 30min），Few-Shot 自学习池（50 成功 + 20 失败样本），格式错误自愈 |
| 笔记内容审核 Agent  | `ModerationHandler @Order(4)`         | 责任链插入 LLM 审核 title+content，检测色情/暴力/广告/虚假信息，置信度三档：>0.8 拦截（Saga 补偿）/0.5-0.8 标记 PENDING_REVIEW /<0.5 放行 |
| ES 混合检索        | `SearchServiceImpl` + `EmbeddingService` | BM25 Top50 + dense_vector kNN Top50 → RRF 融合（k=60）→ Top20；不可用时降级纯 BM25；笔记发布时 Embedding 向量同步入 ES |
| AI 笔记摘要/标签生成   | `AiSummaryHandler @Order(11)`         | 发布时 LLM 生成 50 字摘要 + 3 个话题标签，存入 ES `summary`（text）+ `aiTags`（keyword）字段增强搜索  |

## 环境要求

| 组件             | 版本      |
| -------------- | ------- |
| JDK            | 17+     |
| Maven          | 3.8+    |
| Node.js        | 18+     |
| Docker Desktop | 最新版（推荐） |
| MySQL          | 8.0     |
| Redis          | 7.0+    |
| RabbitMQ       | 3.13+   |
| MongoDB        | 7.0+    |
| Elasticsearch  | 8.15.x  |
| Nacos          | 2.3+    |

## 快速开始

### 1. 启动基础设施（Docker Compose）

```bash
cd my-redbook
docker compose up -d
```

将启动以下服务：

| 容器名                   | 服务            | 端口             | 默认账号              |
| --------------------- | ------------- | -------------- | ----------------- |
| redbook-nacos         | Nacos         | 8848, 9848     | nacos/nacos       |
| redbook-mysql         | MySQL         | 3306 | root/root123      |
| redbook-redis         | Redis         | 6379           | 密码: redis123      |
| redbook-rabbitmq      | RabbitMQ      | 5672, 15672    | admin/rabbitmq123 |
| redbook-mongodb       | MongoDB       | 27017          | root/mongo123     |
| redbook-elasticsearch | Elasticsearch | 9200, 9300     | elastic/es123     |

### 2. 环境变量配置

所有凭据统一放在仓库根目录 `.env` 文件（已被 .gitignore 排除），模板见 `.env.example`：

```bash
cp .env.example .env
# 编辑 .env，填入以下真实值：
#   MYSQL_ROOT_PASSWORD / DB_USERNAME / DB_PASSWORD   MySQL
#   REDIS_PASSWORD / RABBITMQ_PASSWORD / MONGO_PASSWORD   中间件
#   ES_PASSWORD                                       Elasticsearch（与 compose 中 ELASTIC_PASSWORD 一致）
#   INTERNAL_TOKEN                                    服务间内部调用令牌（各服务一致；不设置则内部接口禁用）
#   JWT_SECRET_KEY                                    JWT 密钥（建议 64 位以上随机字符串）
#   DEEPSEEK_API_KEY                                  DeepSeek（AI 审核/分类/摘要，无效时自动降级）
#   EMBEDDING_API_URL / EMBEDDING_API_KEY / EMBEDDING_MODEL   向量化服务（可选，默认禁用）
```

Aliyun OSS 凭证（图片上传，无效时自动降级为无图发布）在 `redbook-service/redbook-service-note/src/main/resources/application.yml` 中配置。

### 3. 启动后端服务（推荐：容器化编排）

```bash
# 编译全部模块（需要 JDK 17+ 与 Maven）
mvn clean package -DskipTests

# 一键启动 9 个后端服务容器（网关/用户/笔记/商品/订单/评论/搜索/消息/AI），
# 复用步骤 1 的基础设施网络，配置全部来自 .env
docker compose -f docker-compose.app.yml --env-file .env up -d --build
```

服务间通过容器名互访（mysql/redis/rabbitmq/nacos/elasticsearch），对外端口与"服务端口"表一致。

备选：宿主机直接 `java -jar` 依次启动（顺序：gateway → user → note → product → order → comment → search → message → ai），或使用 `scripts/start-all.sh`；注意部分受限环境下宿主 JDK 的 NIO 存在兼容问题，此时务必使用容器化方式。

### 4. 启动前端

```bash
cd redbook-frontend
npm install
npm run dev
```

<br />

## 服务端口

| 服务           | 端口    |
| ------------ | ----- |
| API 网关       | 10010 |
| 用户服务         | 8080  |
| 笔记服务         | 8085  |
| 商品服务         | 8095  |
| 订单服务         | 8105  |
| 评论服务         | 8100  |
| 搜索服务         | 8090  |
| 消息服务 (Netty) | 8889（HTTP）/ 8888（WebSocket） |
| AI 服务        | 8091  |
| 前端 (Vite)    | 5173  |

## API 网关路由

所有请求通过网关统一入口，按路径前缀分发：

| 路径前缀          | 目标服务             |
| ------------- | ---------------- |
| `/user/**`    | 用户服务             |
| `/note/**`    | 笔记服务             |
| `/product/**` | 商品服务             |
| `/cart/**`    | 商品服务（购物车）        |
| `/order/**`   | 订单服务             |
| `/comment/**` | 评论服务             |
| `/search/**`  | 搜索服务             |
| `/ai/**`      | AI 服务            |
| `/ws/**`      | 消息服务 (WebSocket) |

购买与搜索接口配置了 Redis 令牌桶限流（replenishRate=10 / burstCapacity=20，按 IP+路径）。


## 环境变量配置

安全相关配置已全部环境变量化，未配置时使用本地开发默认值（localhost + 弱密码），生产环境必须显式配置：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `JWT_SECRET_KEY` | JWT 签名密钥（≥32字符）。**未配置时每次重启随机生成，所有 token 失效** | 随机 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（原 key 已从代码移除，如曾泄露请去后台吊销） | 无 |
| `NACOS_ADDR` | Nacos 注册中心地址 | `localhost:8848` |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_PASSWORD` | MySQL 连接 | `localhost` / `3306` / 代码内默认 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | `localhost` / `6379` / 代码内默认 |
| `RABBITMQ_HOST` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ 连接 | `localhost` / `admin` / 代码内默认 |
| `MONGO_URI`（comment 服务）或 `MONGO_HOST`/`MONGO_PORT` | MongoDB 连接 | `localhost` |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET_NAME` | 阿里云 OSS | 无 |

各服务也支持 `local` profile（`application-local.yml`）覆盖本地连接信息。

## 数据库初始化

`docker/init-sql/` 下的脚本已与 Java 实体逐字段对齐，并包含全部唯一键/索引与种子数据（2 个测试用户：13800000001 / 13800000002；10 个商品，分类为美妆/服饰/食品/数码/家居；3 张优惠券）。`docker compose up -d` 启动基础设施后脚本自动执行。

## 已知设计约定

- 订单只能通过 `POST /product/buyProduct`（Redis Lua 预扣库存 → MQ → MySQL 原子扣减）创建，待支付订单 15 分钟后由延迟消息自动关单并回补库存，另有定时任务兜底；
- 网关统一注入 `userId` 请求头并剥离客户端伪造的同名头，下游服务从请求头读取用户身份；
- Feign 调用统一配置了超时（connect 2s / read 5s），笔记服务的评论/用户调用配置了 fallback 降级；
- 短信通道未接入，验证码在用户服务日志中以打码手机号形式输出（仅供联调，生产必须接入短信后移除）；
- 测试账号：13800000001 / 13800000002（配合日志中的验证码登录）。
