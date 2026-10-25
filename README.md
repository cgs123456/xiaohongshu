# RedBook — 仿小红书社交电商全栈平台

> Spring Cloud 微服务 + Vue 3 的社交电商平台：笔记社区、SKU 级库存交易、AI 购物助手与内容审核、推拉结合信息流。

[![CI](https://github.com/cgs123456/xiaohongshu/actions/workflows/ci.yml/badge.svg)](https://github.com/cgs123456/xiaohongshu/actions/workflows/ci.yml)

9 个后端微服务 + Vue 3 前端，6 类中间件，从登录发帖到下单支付的完整闭环。已内置全链路 E2E 脚本与 CI，`master` 每次推送自动构建前后端。

## 目录

- [快速开始](#快速开始)
- [功能特性](#功能特性)
- [架构一览](#架构一览)
- [配置](#配置)
- [测试与质量](#测试与质量)
- [已知约定与限制](#已知约定与限制)
- [许可证](#许可证)

## 快速开始

需 JDK 17+、Maven 3.8+、Node 18+、Docker（含 compose 插件）。

```bash
# 1. 基础设施（MySQL/Redis/RabbitMQ/MongoDB/ES/Nacos，含建库与种子数据）
cp .env.example .env && vim .env     # 填入各中间件密码与 JWT 密钥（Windows 下可用 notepad）
docker compose up -d

# 2. 后端 9 服务（网关/用户/笔记/商品/订单/评论/搜索/消息/AI，一次性容器化）
mvn clean package -DskipTests
docker compose -f docker-compose.app.yml --env-file .env up -d --build

# 3. 前端
cd redbook-frontend && npm install && npm run dev   # http://localhost:5173
```

验证：`bash scripts/e2e-test.sh` 跑通登录→购物→结算→支付→发帖→搜索全链路（手机号在 `.env` 的 `E2E_PHONE` 配置）。

> [!TIP]
> 受限环境（宿主 JDK NIO 兼容问题）务必用上面的容器化方式启动，不要宿主 `java -jar`。

## 功能特性

**社区**
- 图文笔记发布：责任链 12 环节（上传→定位→LLM 审核→敏感词→话题→ES 同步），AI 生成摘要与话题标签
- 信息流：发现页分类浏览；关注流推拉结合（发布扇出收件箱 + 读侧 DB 兜底，大 V 自动降级拉模式）
- 互动：点赞/收藏（幂等 + 事务后异步）、评论（MongoDB 楼中楼）、Netty WebSocket 实时通知 + 离线补发

**交易**
- SKU 分规格库存：Redis Lua 原子预扣 → MQ 异步落库，商品/规格两级同事务
- 双下单路径：购物车多商品结算（优惠券门槛校验 + 商品快照）与 AI Agent 直购，统一订单明细
- 支付网关抽象：`PaymentGateway` 接口 + Mock 默认实现，接入真实渠道只需新增实现类；异步回调 + 定时查单补偿（回调丢失自动对账）
- 超时关单：15 分钟延迟消息 + 兜底扫描双保险，按明细回补对应 SKU 库存、退还优惠券，失败进补偿重试队列
- 可靠性：MQ messageId 幂等、指数退避重试、死信落库 `rb_order_dead_message`

**AI（DeepSeek + Embedding，无 Key 自动降级）**
- 购物 Agent：多轮对话 + 5 工具 Function Calling（搜索/详情/加购/下单/查券），Few-Shot 自学习
- 内容审核 Agent：三档置信度（拦截/人工复审/放行）；搜索 BM25 + 向量混合检索（RRF 融合）

**前端体验**
- 设计令牌体系（CSS 变量唯一事实源）、lucide 图标语言、骨架屏渐进加载、空/错态组件化、弹窗移动端全屏
- 游客可浏览商城，写操作才引导登录

## 架构一览

请求统一走网关（10010）鉴权、注入 `userId` 头并限流；服务间 OpenFeign（附带内部令牌）。消息 DTO 下沉 `redbook-common` 单一副本。

| 服务 | 端口 | 职责 |
| --- | --- | --- |
| gateway | 10010 | 鉴权/白名单/限流/内部路由封堵 |
| user | 8080 | 手机验证码登录、关注、批量用户查询 |
| note | 8085 | 笔记、互动、信息流、Feed 扇出 |
| product | 8095 | 商品、SKU 库存、购物车、优惠券、购物 Agent |
| order | 8105 | 订单、支付、关单补偿、查单对账 |
| comment | 8100 | 评论（MongoDB） |
| search | 8090 | ES 混合检索、热搜、历史 |
| message | 8889/8888 | Netty WebSocket 通知 |
| ai | 8091 | LLM 能力封装 |

技术栈明细（Spring Boot 3.3.5 / MyBatis-Plus / Redis / RabbitMQ / ES 8.15 / MongoDB / Vue 3.5 / Vite 6 等）见 [项目审查与优化建议报告.md](项目审查与优化建议报告.md)，其中附录一至九含全部设计决策与演进记录。

## 配置

凭据统一在根目录 `.env`（已被 .gitignore 排除），完整模板见 [`.env.example`](.env.example)。关键项：

| 变量 | 说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` / `REDIS_PASSWORD` / `RABBITMQ_PASSWORD` / `MONGO_PASSWORD` / `ES_PASSWORD` | 中间件密码（与 compose 一致） |
| `JWT_SECRET_KEY` | JWT 密钥（≥32 字符；不设则每次重启随机、全员掉线） |
| `INTERNAL_TOKEN` | 服务间内部调用令牌（不设则内部接口禁用） |
| `DEEPSEEK_API_KEY` | AI 能力（审核/摘要/Agent），无效自动降级 |
| `E2E_PHONE` | E2E 测试手机号 |

Aliyun OSS 图片上传凭证在 note 服务 `application.yml`（无效时降级为无图发布）。

## 测试与质量

- **E2E**：`bash scripts/e2e-test.sh` 全链路回归；另有 `feed-test.sh`（推拉信息流）、`sku-test.sh`（分库存）、`ablation-test.sh`（机制消融）、`contract-test.sh`（跨服务序列化探针）
- **UI 冒烟**：`cd redbook-frontend && npx playwright test`（需本地后端运行；chromium 下载慢时 `PLAYWRIGHT_DOWNLOAD_HOST=https://npmmirror.com/mirrors/playwright/`）
- **CI**：GitHub Actions 每次推送构建前端（vue-tsc + vite）与后端（mvn package）

## 已知约定与限制

- 短信通道未接入，验证码在用户服务日志以打码手机号形式输出（联调用，生产须接短信后移除）
- 支付为 Mock 渠道（同步完成）；跳转类渠道接入时实现 `PaymentGateway` 三方法即可，查单补偿与流水记账已就绪
- 笔记审核走 LLM 三档策略，人工复审队列未做管理界面（`PENDING_REVIEW` 数据可在库中查）
- 种子数据含 2 个测试用户与 10 个商品（`docker/init-sql/`，验证码登录）

## 许可证

UNLICENSED（个人学习项目，未声明开源许可证；如需开源请自行添加 LICENSE 文件并更新本节）。
