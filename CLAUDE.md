# 星选商城 — 项目开发指南（AI 协作编码规范）

## 项目概述

**Spring Cloud Alibaba 分布式微服务电商平台**：7 个业务服务 + API 网关 + 双前端（Vue 2 购物端 / React 管理端）+ AI Agent/MCP。数据库为单库 `shop`，多服务按表归属读写。

固定版本组合（不要随意更改）：**JDK 17** | Spring Boot 3.4.6 | Spring Cloud 2024.0.1 | Spring Cloud Alibaba 2023.0.3.4 | Spring AI 1.0.0 | MyBatis-Plus 3.5.10 | jjwt 0.12.6 | mybatis-spring 3.0.4（父 POM 显式锁定，勿移除——MP 3.5.10 传递的 2.1.2 在 Spring 6.2 下 Mapper 注册会直接启动失败）

## 目录结构与表归属

```
shopping/
├── pom.xml                # 父 POM：聚合 + dependencyManagement
├── shop-common/           # Result/XmException/JwtUtil/UserContext/Redis序列化/Feign身份透传
├── shop-feign-api/        # @FeignClient 接口 + 跨服务 DTO
├── shop-gateway/  :8080   # SCG 网关 + JWT 鉴权过滤器（唯一公网入口）
├── shop-user/     :8101   # 登录/JWT 签发/BCrypt/用户/地址
├── shop-product/  :8102   # 商品/分类/轮播/图集/秒杀抢购/ES搜索(可降级MySQL)
├── shop-cart/     :8103   # 购物车/收藏
├── shop-order/    :8104   # 订单/秒杀MQ消费/模拟支付/超时取消回滚/售后
├── shop-admin/    :8105   # 管理聚合接口 + 统计报表(经 Feign 聚合)
├── shop-chat/     :8106   # 购物/管理双 Agent(SSE) + MCP Server（直连，不经网关）
├── shop-frontend/         # Vue 2 购物端（dev 7080）
├── shop-admin-web/        # React 管理端（dev 7090）
├── svc.sh / svc.bat       # 服务启停脚本（双平台，含 fe/web 前端启动，日志在 <项目根>/logs/）
├── nacos-config-upload.sh # Nacos 公共配置上传脚本（group=SHOP）
├── sql/shop.sql           # 全量初始化脚本（15 表 + 种子数据，可重复执行）
├── deploy/                # 部署指南、Nacos 公共配置源(deploy/nacos/shop-common.yml)、docker-compose、Nginx 示例
└── demo-materials/        # 项目效果图
```

**表归属**（跨域写必须走 Feign，禁止跨服务直写他域表）：
- product：`product` `category` `carousel` `product_picture` `seckill_time` `seckill_product`
- order：`order` `seckill_message_record` `payment_record` `aftersale_record` `user_message`
- user：`user` `user_address`
- cart：`shopping_cart` `collect`

## 核心架构约定

- **鉴权**：JWT HS256，payload `{uid, username, role, jti}`，exp 7 天；网关校验并注入 `X-User-Id/X-User-Name/X-User-Role`（先剥客户端伪造的同名 header）；业务服务经 common 的 UserContextInterceptor 注入 UserContext 取身份；登出走 Redis 黑名单 `auth:logout:{jti}` 网关统一拦截。
- **服务间调用**：全走 shop-feign-api 的 OpenFeign 接口（如下单扣库存 → `ProductClient.decrStock`）；common 提供统一 Result 解包 ErrorDecoder 与身份透传拦截器（FeignIdentityInterceptor 借 UserContext 透传 X-User-*）。
- **Agent 工具调用**一律走 Feign 不直连 DB；用户身份经 `ChatClient.prompt().toolContext()` 注入，工具用 `ToolContext` 取，绝不让模型编造 userId；**SSE 工具调用跑在 reactor 线程，ThreadLocal 不传播**——工具内须在工具线程上重建 UserContext。MCP Server 用 **webmvc 版 starter**（webflux 版与现有 Flux SSE controller 冲突），8106 直连不经网关；MCP 通道无登录态，写类工具由工具内二次校验拦截。
- **配置中心**：bootstrap.yml + Nacos（group `SHOP`，dataId `shop-` 前缀），公共配置 `shop-common.yml`（源文件 deploy/nacos/shop-common.yml，改动后须重跑 `nacos-config-upload.sh` 并**重启服务**）；优先级：**Nacos 远端 > JVM -D > env > 本地 yml（含 application-local.yml）**。三条铁律：①敏感值禁上 Nacos；②需 local 覆盖的键（如 datasource.url）禁上；③需 env/-D 覆盖的键必须写成 `${ENV:default}` 占位符。
- **搜索降级**：`shop.search.es-enabled=false`（默认）或 ES 连接失败 → 自动熔断降级 MySQL LIKE；重建索引成功后熔断标记自动复位。ES 依赖服务端 analysis-ik 插件。
- **秒杀链路**：Sentinel 限流 → Redis 防重 + Lua 预扣 → 本地消息表 SENT → MQ `seckill_order` → order 消费幂等 CAS 落库 → TTL 30min 死信超时取消（CAS 与支付竞争互斥）+ Redis/DB 库存回滚。消息表状态机：product 抢购成功写 SENT，order 消费 CAS `SENT→CONSUMED` 抢处理权，失败回退重试，死信兜底 FAILED+回滚。RabbitMQ 用原生 TTL+死信，**不装延迟插件**。
- **internal 接口双拦**：`/order/internal/`、`/product/internal/`、`/user/internal/` 由网关一律 403；服务端 internal 控制器内再补 ADMIN 硬校验（纵深防御，admin 经 FeignIdentityInterceptor 透传不受影响）。
- **交易安全**：下单不信任客户端——价格一律服务端回查、num 钳 1~5、收件地址随单快照落库；支付/取消用 CAS 互斥（`payIfPending`/`cancelIfPending` 只赢一个）；售后仅退款状态机 `0待处理→1同意|2已拒绝`（拒绝后可重新申请新行，靠事务内锁父订单行防重复申请而非唯一键）。

## 接口路径约定（网关路由）

`/user/**`→user；`/product|/category|/productPicture|/resources/**` 与 `/seckill/**`→product；`/cart|/collect/**`→cart；`/order|/pay/**`→order；`/admin/**` 与 `/agent/admin/**`→admin/chat 且需 ADMIN 角色；`/chat/**`→chat（GET 白名单含匿名）；`/mcp` 不走网关（8106 直连）。

## 编码规范

### 后端

- **身份获取**：Controller 一律从 header/UserContext 取身份，**禁止路径传 userId**；对外接口须校验资源归属防越权。
- **MyBatis-Plus**：pojo 字段必须与表列对齐，非表字段标 `@TableField(exist=false)`；分页用 PaginationInnerInterceptor（maxLimit 200）且依赖 mybatis-plus-jsqlparser。
- **shop-common 模块**：新写公共 Bean 若依赖 optional/provided 库，装配必须用 `@ConditionalOnClass(name="...")` 字符串形式（否则未引该依赖的服务启动即挂）。
- **Cookie**：host-only，禁止 setDomain（会被 Cookie 规范拒绝导致登录态丢失）。
- **XmException**：保持 `(ExceptionEnum)` 单参构造兼容（同时存在 message 透传的 (ExceptionEnum, String) 双参构造）。
- **Feign 契约**：接口与实现签名变动必须同步 shop-feign-api，跨服务 DTO 放 feign-api 不放各服务。
- **雪花 IdWorker**：各服务实例 workerId 必须错开，lastTimestamp 不加 static（防同毫秒撞号）。
- **MQ 配置**：order 的 `acknowledge-mode: manual` 只能留在 order 本地，**严禁上移 Nacos 公共配置**（会波及 product 消费端）。
- **SSE**：全服务 MVC 异步超时统一 180000（经 Nacos shop-common.yml 下发 + 网关 httpclient 180s）；chat 本地 yml 显式保留 mvc 180000 双保险。
- **reactor 线程无 ThreadLocal**：SSE controller 里 uid 等身份信息须在请求线程捕获进闭包，doFinally/doOnCancel 内禁止读 UserContext。
- **测试**：测试类不要依赖真实业务数据，聚焦 Spring 上下文装配验证。

### 前端

- Vue2 渲染 AI Markdown：`marked@4 + DOMPurify`（bot 气泡 v-html 消毒，用户输出纯文本）；React 端用 `react-markdown + remark-gfm` 渲染为元素，**禁加 rehype-raw**。
- 前端"停止生成"：`EventSource.close()` 不触发 onerror/done，必须自行 finish 收尾。
- 图片前缀：`VUE_APP_IMG_TARGET`（默认 `/` 同源托管）；ES/MySQL 高亮统一 `<em>` 标签，前端做 `<em>` 白名单清洗防 XSS。

### 通用

- **改依赖/版本后必须 `mvn clean package`**：不 clean 时（尤其 `-T 1C` 并行）fat jar 可能残留旧依赖，dependency:tree 显示新版但运行时是旧版。
- curl 中文 query 必须 `--data-urlencode`（不编码会得到 Tomcat 400 HTML 页，不是接口问题）。
- `application-local.yml` 含真实密钥已 gitignore，**不要提交**；需要本地覆盖连接信息/密钥时优先写 local 文件或用环境变量。
- 注释、文案、文档一律中文；提交信息简短中文。
- 服务器地址等环境信息一律不出现在文档/脚本中，用 `你的服务器地址` 占位（端口与 localhost 可保留）。

## 构建与验证

```bash
# 后端全模块构建（改过依赖版本必须 clean）
mvn clean package -DskipTests

# 服务启停（双平台命令一致；日志在项目根 logs/）
bash svc.sh start|stop|status|restart [gateway|user|product|cart|order|admin|chat|fe|web]
svc.bat start|stop|status|restart [gateway|user|product|cart|order|admin|chat|fe|web]
#    不带目标=全部 7 个后端；fe=购物端(7080) web=管理端(7090)
#    额外 JVM 参数：SVC_JAVA_OPTS="-Dshop.order.pay-timeout-ms=15000"（如秒杀超时演示短 TTL）

# 前端 dev
cd shop-frontend  && npm run serve   # 7080，代理 /api → localhost:8080（网关）
cd shop-admin-web  && npm run dev     # 7090
npm run build                         # 生产构建（购物端需 VUE_APP_IMG_TARGET=/）

# 数据库初始化 / Nacos 公共配置上传
mysql -u root -p --default-character-set=utf8mb4 < sql/shop.sql
NACOS_ADDR=localhost:8848 bash nacos-config-upload.sh
```

最小验证清单：数据库初始化 → Nacos 配置上传 → `svc.sh start` 后 `status` 全 UP → 登录 admin/admin123 → 搜索（降级模式）→ 网关 401/403 越权抽查。

## 配置体系约定

- 服务特有配置留各自 application.yml；公共配置只进 `shop-common.yml`（改源文件 → 跑上传脚本 → 重启服务；配置中心是分发不是热更新）。
- 环境变量：`NACOS_ADDR`（必填）、`NACOS_USERNAME/PASSWORD`、`DB_URL/DB_USERNAME/DB_PASSWORD`、`REDIS_HOST/PORT`、`MQ_HOST/PORT/USERNAME/PASSWORD`、`ES_URI/ES_ENABLED`、`DEEPSEEK_API_KEY`、`JWT_SECRET`（生产必换）、`CORS_ORIGINS`、`SPRING_PROFILES_ACTIVE`、`SVC_JAVA_OPTS`。
- AI 对话使用 deepseek-chat，key 从环境变量 `DEEPSEEK_API_KEY` 注入。
- 日志级别：root 默认 info（`LOG_LEVEL` 可调），业务包 `com.shop` 用 `SHOP_LOG_LEVEL=debug` 打开业务与 MyBatis SQL 日志。

## 部署安全底线

- 业务端口 8101~8106 **不对公网开放**（X-User-* header 信任边界在网关内），只暴露网关 8080 与 Nginx。
- `JWT_SECRET` 生产必换；MCP 8106 加白名单或限内网访问。
- Nginx 须剥外部伪造的 `X-User-*` header（与网关形成双保险）。
- 详细部署步骤、验证清单与 FAQ 见 `deploy/README.md`。