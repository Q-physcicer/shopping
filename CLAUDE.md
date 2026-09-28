# 星选商城 — 分布式升级项目指南

## 项目概述

单体 Spring Boot 商城升级为 **Spring Cloud Alibaba 分布式微服务 + 双前端 + AI Agent/MCP**。
购物端：Vue2（`shop-frontend`）；管理端：React + Ant Design Pro（`shop-admin-web`）。

## 目标架构

```
shopping/
├── pom.xml                # 父 POM：聚合 + dependencyManagement
├── shop-common/           # Result/XmException/JwtUtil/UserContext/Redis序列化
├── shop-feign-api/        # @FeignClient 接口 + 跨服务 DTO
├── shop-gateway/  :8080   # SCG 网关 + JWT 鉴权过滤器
├── shop-user/     :8101   # 登录/JWT 签发/BCrypt
├── shop-product/  :8102   # 商品/分类/轮播/秒杀抢购/ES搜索/降级MySQL
├── shop-cart/     :8103   # 购物车/收藏
├── shop-order/    :8104   # 订单/秒杀MQ消费/模拟支付/超时回滚
├── shop-admin/    :8105   # 管理聚合接口 + 统计报表
├── shop-chat/     :8106   # 双Agent + MCP Server(SSE)
├── shop-frontend/         # Vue2 购物端 (dev 7080)
├── shop-admin-web/        # React 管理端 (dev 7090)
└── sql/                   # DDL（V2__upgrade.sql 为增量）
```

### 关键技术决策（已拍板，不要更改）

- **版本组合**：Spring Boot 3.4.6 + Spring Cloud 2024.0.1 + Spring Cloud Alibaba 2023.0.3.4 + Spring AI 1.0.0 + jjwt 0.12.6 + MyBatis-Plus 3.5.10，JDK 17
- **7 服务不分库**：共用 `shopmanagement` 单库，按表归属读写（product 拥有 product/category/carousel/product_picture/seckill_*；order 拥有 order/seckill_message_record；user 拥有 user；cart 拥有 shopping_cart/collect）。跨域写必须走 Feign（如下单扣库存 → `ProductClient.decrStock`）
- **鉴权**：JWT HS256，payload `{uid, username, role, jti}`，exp 7 天；Cookie 名 `XM_TOKEN` 保留但值换 JWT；网关统一鉴权后注入 `X-User-Id/X-User-Role` header（必须先剥客户端伪造的同名 header）；Controller 一律从 header 取身份，禁止路径传 userId
- **Agent 工具调用全走 Feign**，不直连 DB；用户身份经 `ChatClient.prompt().toolContext()` 注入，工具用 `ToolContext` 取，绝不让模型编造 userId
- **MCP Server**：`spring-ai-starter-mcp-server-webmvc`（不能用 webflux 版，SSE controller 是 webmvc + Flux 混用），8106 直连不走网关
- **ES 降级**：`shop.search.es-enabled=false` 或连接失败 → 自动降级 MySQL LIKE，保证没装 ES 也能跑
- **秒杀**：Redis Lua 预扣（现有脚本复用）→ 本地消息表(SENT) → MQ `seckill_order` → order 消费落库（幂等 CAS）→ TTL 30min 死信超时取消 + 库存回滚；RabbitMQ 用原生 TTL+死信，不装延迟插件
- **支付**：模拟收银台，`POST /pay/mock/{orderId}` 回调改状态
- **密码**：BCrypt；登录时兼容旧 MD5 并透明升级落库
- **部署**：本机开发（application-local.yml，gitignore）+ 服务器部署（application-server.yml）；DeepSeek key 从环境变量 `DEEPSEEK_API_KEY` 注入

### 接口路径约定（网关路由）

`/user/**`→user；`/product|/category|/productPicture|/resources/**`→product；`/seckill/**`→product；`/cart|/collect/**`→cart；`/order|/pay/**`→order；`/admin/**`→admin（需 ADMIN 角色）；`/chat|/agent/**`→chat；`/mcp` 不走网关。

## 技术风险清单

| 风险 | 说明 | 应对 |
|---|---|---|
| **SCA 版本兼容** | SCA 2023.0.3.4 官方未对应 SC 2024.0 train，可能与 spring-cloud-commons 4.2.x 冲突 | P0 必须先版本冒烟：起 user 注册 Nacos + 网关转发成功才算通过；冲突则回退 SCA 2023.0.3.2 |
| **SSE 过网关** | SCG 对 SSE 支持需禁缓冲/改写类 filter；response-timeout 要拉长 | `/chat/**` 路由不加 ResponseBodyModifier；`httpclient.response-timeout: 180s`；Nginx 生产关 `proxy_buffering` |
| **MCP starter 与 webmvc** | spring-ai-starter-mcp-server-webflux 会与现有 `Flux<ServerSentEvent>` controller 冲突 | 必须用 webmvc 版 starter |
| **DeepSeek Function Calling** | deepseek-chat 支持 FC 但偶发工具调用幻觉 | 工具描述写清晰；身份一律从 ToolContext 取；下单前工具内二次校验登录态 |
| **ES 未安装不阻塞** | 服务器可能先跑起来再装 ES | 搜索开关 + try-catch 熔断标记降级 |
| **Feign + Result 解包** | 各服务返回 `Result<T>` 包装，Feign 调用需要统一解包与错误传播 | shop-feign-api 提供 ErrorDecoder + fallback |
| **存量数据兼容** | 旧 MD5 密码、旧订单无 status 字段 | 登录透明升级；DDL 存量订单 status 初始化为 1 |
| **秒杀重复消费** | MQ 可能重投 | message_id 唯一索引 + 消费端 CAS(UPDATE WHERE status='SENT') |
| **Sentinel 规则丢失** | 内存规则重启即丢 | 规则持久化到 Nacos dataId `shop-product-sentinel-flow.json` |
| **多实例定时任务重复执行** | SeckillTask 无分布式锁 | Redis setnx 锁 |

## 已知历史坑（来自过往会话）

- 测试类不要依赖真实业务数据，聚焦 Spring 上下文装配验证
- SSE 流式验证不能依赖 devServer（已关 gzip），要独立 curl 验证
- MVC 异步超时已设 180s（`spring.mvc.async.request-timeout`），拆分后各服务与网关都要对齐
- `application-local.yml` 含真实密钥，已 gitignore，**不要提交**
- 轮播数据中含绝对域名 `http://47.115.85.237:3000`，已由 V2__upgrade.sql 改相对路径
- **mybatis-spring 版本锁定**（P0 实测）：MP 3.5.10 传递 mybatis-spring 2.1.2，在 Spring 6.2 下注册 Mapper Bean 报 `factoryBeanObjectType: java.lang.String` 直接启动失败。父 POM dependencyManagement 已锁 `org.mybatis:mybatis-spring:3.0.4`，**不要移除**
- **改版本后必须 `mvn clean package`**：不 clean 时（尤其 `-T 1C` 并行）fat jar 里可能残留旧依赖，dependency:tree 显示新版但运行时是旧版，极具迷惑性
- **curl 中文 query 必须 `--data-urlencode`**：`curl "http://...?message=中文"` 未编码会得到 Tomcat 400（HTTP 400 HTML 页），不是接口问题
- 服务本地启停用 `bash scripts/svc.sh start|stop|status|restart [服务名]`（日志在 /tmp/shop-logs/<服务>.log）；stop 后 JVM 优雅退出需几秒，立即 start 会 [SKIP]，等 10s 再启动
- 本机环境：MySQL(localhost:3306)/Redis(6379)/RabbitMQ(5672) 本地已装；Nacos 与 ES 用服务器 8.130.22.3（8848/9200），无 docker。ES 9200 当前未就绪（P3 前需确认服务端已监听 + 防火墙放行）
- 旧单体 shop-backend 已迁移完毕并整体移入 .trash/shop-backend-migrated（git 未提交的删除记录待用户提交时生效）；xmall/ 保留仅作 P5 管理端布局参考

## P1 鉴权改造后的既定事实

- **测试账号**：admin/admin123（role=ADMIN）；p1test/Test123456（普通用户）。admin 密码已透明升级为 BCrypt
- **身份 headers**：网关 `AuthGlobalFilter` 校验 JWT 后注入 `X-User-Id/X-User-Name/X-User-Role` 并**先剥客户端伪造值**；业务服务用 `UserContext.getUserId()` 取身份（common 的 UserContextInterceptor 自动注册）
- **坑：Cookie 禁止 setDomain**——`cookie.setDomain("localhost")` 会被 Cookie 规范拒绝导致登录态丢失，CookieUtil 已改为 host-only cookie（老 getDomainName 逻辑已删）
- **坑：common 中新写公共 Bean 若依赖 optional/provided 库，必须 `@ConditionalOnClass(name="...")` 字符串形式**——否则没引该依赖的服务启动即挂（JwtUtil 踩过）
- 业务 API 改动（购物端同步改完）：`GET /cart/user`、`POST /cart/product/{pid}`、`PUT /cart/user/num/{cartId}/{num}`、`DELETE /cart/user/{cartId}`、`GET|POST|DELETE /collect/user[...]`——路径不再带 userId；前端 401 拦截改看 HTTP 状态码
- 登出黑名单：`POST /user/logout` → Redis `auth:logout:{jti}`，网关统一拦截

## P2 秒杀闭环后的既定事实

- **消息表状态机**（seckill_message_record，product/order 双端共写的基础设施表）：product 端抢购成功写 SENT、发布 NACK 置 FAILED；order 端消费 CAS `SENT→CONSUMED` 抢处理权，失败回退 `CONSUMED→SENT` 重试（3 次退避），死信兜底置 FAILED+Redis 库存回滚+清防重集合
- **秒杀链路**（40 并发实测通过）：Sentinel 限流(QPS1000+热点参数 seckillId 200) → SADD 防重(key `seckill:product:user:set:{id}`) → Lua 预扣 → 消息表 SENT → MQ `seckill_order` → order 消费幂等落库(status=0 待支付) → TTL 30min 死信超时取消(CAS 与支付竞争防双写)+库存回滚（秒杀单/普通单都回滚）。压缩 TTL 演示用 `SVC_JAVA_OPTS="-Dshop.order.pay-timeout-ms=15000" bash scripts/svc.sh start order`
- **坑：RabbitMQ per-message TTL 队头堵塞**——消息级 expiration 的死信只按队头逐条过期，30min 老消息会堵住后面 15s 的演示消息；验证短 TTL 前必须先清空 `order.pay.timeout.queue`（15672 管理 API 或管理台）
- **坑：MyBatis-Plus pojo 字段必须与表列对齐**——SeckillProduct 的 version/deleted/createTime/updateTime 是遗留字段，已标 `@TableField(exist=false)`，否则 selectById 生成非法列 SQL 导致 MQ 消费反复失败
- 验证脚本：`scripts/p2-verify.sh`（40 用户 20 并发抢 10 库存断言无超卖）、`scripts/p2-timeout-verify.sh`（短 TTL 取消+回滚）；测试用户 bkt1..bkt40/Test123456
- 抢购前端交互：返回"排队中"后轮询 `GET /seckill/product/result/{seckillId}`，data 为 SENT(排队中)/CONSUMED(成功)/FAILED(失败已回滚)/null(未参与)
- 已修复历史 bug：下单清购物车（原 deleteById(cart) 传实体恒删 0 行）、IdWorker 从未初始化（下单必 NPE）、CookieUtil.delCookie 构造后未 addCookie
- 秒杀活动由 SeckillTask 每日 15:00 定时重建（Redis setnx 分布式锁）；手动造场次用 `scripts/p2-seed.sql`（注意时间戳单位是**毫秒**）

## P3 搜索实现后的既定事实

- 搜索接口：`GET /product/search?keyword=&categoryId=&sort=relevance|sales|price&page=&size=`（公开只读）；返回 `{total, page, size, engine, list}`，engine 标记 elasticsearch/mysql
- **降级策略**：`shop.search.es-enabled`（env `ES_ENABLED`，默认 false）关闭或 ES 异常时自动熔断降级 MySQL LIKE；熔断标记在内存，重建索引成功后自动复位——服务器没装好 ES 系统照样能搜
- ES 分词依赖服务端 `analysis-ik` 插件（ik_max_word 建索引/ik_smart 检索，productName^3/productTitle^2/productIntro 权重）；**服务器 8.130.22.3:9200 目前连不通，就绪后设 ES_ENABLED=true 重启 product 即切 ES**（代码已就绪，启动自动建索引+全量导入）
- 高亮统一 `<em>` 标签（ES highlight 与 MySQL 替换两种实现），前端 SearchView 做了 `<em>` 白名单清洗防 XSS
- 前端搜索：顶栏搜索框 → `/search?keyword=` 独立搜索页（分类筛选/排序/分页/高亮）；顺带发现顶栏全局图片前缀 `$target=http://47.115.85.237:3000/`（老图片服务器），P7 部署时统一切到新服务器 Nginx
- 前端搜索页文件：`shop-frontend/src/views/SearchView.vue` + 路由 `/search`

## P4 Agent/MCP 后的既定事实

- **端点**：`GET /chat/stream`（购物 Agent 小智，网关 GET 白名单含匿名）、`GET /agent/admin/stream`（管理 Agent，网关 `/admin|/agent/admin` 走 ADMIN 校验——注意 admin-stream 在 gateway 白名单 `/chat/**` 之外）、`POST /chat/reset`
- **工具集**（ShopTools，全走 Feign）：searchProducts/getProductDetail/addToCart/getMyCart/placeOrder/getMyOrders；AdminTools 骨架 adminSearchProducts（P5 扩全）
- **身份链（关键设计）**：网关 JWT→X-User-Id→UserContext(ThreadLocal)→controller 放入 `prompt().toolContext(Map)`→工具方法从 ToolContext 取；**SSE 工具调用跑在 reactor 线程（boundedElastic），ThreadLocal 不传播**——ShopTools.callAsUser 在工具线程上重建 UserContext 供 FeignIdentityInterceptor 透传 X-User-*（P4 踩坑实测修复）
- **Feign 身份透传**：common 的 FeignIdentityInterceptor（@ConditionalOnClass 装配），chat/order 等启用 @EnableFeignClients 的服务生效；信任边界=业务端口不对公网开放（P7 部署要求）
- **会话记忆**：RedisChatMemoryRepository（List `chat:memory:{shop|admin}:u{userId}:{cid}`，窗口 20 条 TTL 7 天），跨用户隔离；MessageChatMemoryAdvisor 持久化多轮
- **MCP Server**（8106 直连不经网关）：`GET /sse` 握手→`POST /mcp/message?sessionId=...`，tools/list 已验证暴露全部 7 工具（含 JSON Schema）；Claude Desktop 接入 `"url":"http://<ip>:8106/sse"`。MCP 通道无登录态→写类工具被"未登录"文案拦截
- **坑：MCP starter 只暴露容器中的 ToolCallbackProvider bean**——ChatClient 的 defaultTools(...) 不进 MCP，必须另建 MethodToolCallbackProvider bean（AgentConfig.mcpToolProvider）
- **实测**：登录用户「帮我把商品ID为4的Redmi 8下单一单」→ Agent 查详情→确认→Feign 下单→真实订单落库（status=0 待支付 30min TTL）→下一轮对话能凭记忆复述订单号；匿名只读可用、写操作引导登录

## P5 管理端后的既定事实

- **admin 服务**：`/admin/**` 网关校验 ADMIN；AdminProductController（上架/改价改库存/下架[库存置0]）、AdminOpsController（分类/轮播/秒杀新增/用户角色/订单分页[Feign]/统计聚合[Feign×3]/ES重建[Feign]）
- **统计口径**：GMV=status IN(1,3) 的 price*num；多服务 internal 接口：`/product/internal/stats/top-sales|low-stock`、`/order/internal/stats/gmv`、`/user/internal/stats/count|growth`（V3 增量给 user 加了 created_at 列，存量行为迁移时刻）
- **ES 增量同步链**（admin 发 MQ → product 消费同步）：admin 改商品 → publish topic exchange `shop.product.change`(key=product.update) → product `ProductChangeConsumer` 读库 syncOne；同步失败不阻塞管理操作，重建按钮兜底
- **管理 Agent**（chat）：AdminTools 五工具（adminSearchProducts/publishProduct/updateProduct/getStats），写操作**工具内硬校验 role=ADMIN**（chat 直连 Feign 不经网关，网关 JWT 角色之外的第二道闸）；MCP 外部通道同样被拦
- **网关**：`/agent/admin/**` 路由到 chat 服务（不设 ADMIN 路由位），AuthGlobalFilter 对 `/admin|/agent/admin` 都做 ADMIN 校验
- **React 管理端**（`shop-admin-web/`，Vite+React18+antd5+pro-components+recharts，dev 7090）：Login/Dashboard(GMV曲线/注册柱状/Top表)/Products/Seckill/Carousels/Orders/Users/AgentChat(EventSource SSE)/Settings；JWT 存 localStorage + Authorization Bearer，登录时后端 Set-Cookie 供 EventSource 用；`npm run build` 已通过
- React 端 axios 拦截器直接解包 Result 返回 data（code!=1 reject）；HTTP 401 踢回登录页

## P6 模拟支付后的既定事实

- **链路**：购物车结算下下单返回 orderId → 前端直跳 `/pay/:orderId` 模拟收银台（金额/商品数/状态态）→ `POST /pay/mock/{orderId}` → `OrderMapper.payIfPending` CAS(0→1) + payment_record 流水（雪花 payNo "MOCK"+id）→ 轮询确认 → 支付成功页
- **状态机闭环**：`payIfPending` 与 `cancelIfPending`（超时取消）CAS 互斥，支付与取消只赢一个；已取消订单支付返回"不可支付"；PayController 按 userId 校验订单归属防越权（实测通过）
- 订单页（OrderVirw.vue）显示状态徽标（待支付/已支付/已取消[超时]/已完成）+ 秒杀单标识 + 待支付"去支付"按钮；addOrder 响应带 orderId
- **ES 现状**：~~7.17 无 ik 降级~~ → **已在 P7 收尾切换为 ES 8.19.20 + ik 正式模式**（见 P7 段）；降级机制保留，ES 异常时自动回退 MySQL 不影响可用性

## P7 部署打磨后的既定事实（收尾联调 2026-09-25 更新）

- **ES 已切换为 Elasticsearch 8.19.20 + analysis-ik 模式**（联调实测）：启动自动建索引+全量导入 35 条；「骁龙」等中文语义分词搜索/`<em>` 高亮/分类过滤/销量价格排序全可用；admin 上架→MQ 同步→3 秒内可搜、下架→即时移出索引、`/admin/es/rebuild` 全量重建 35 条——**本地启用方式：`SVC_JAVA_OPTS="-Dshop.search.es-enabled=true" bash scripts/svc.sh start product`（或 env ES_ENABLED=true）**
- 已修复：`AdminProductController.create` 上架未传 productTitle 时 NOT NULL 报错（现用商品名兜底截断）
- **已清理无关代码**：SeckillProductController 过渡新增接口与 ServiceImpl.addSeckillProduct 死代码已下线（秒杀管理仅走 /admin/seckill）；scripts/migrate-p0.sh 删除；xmall/（参考项目）与 images/（旧产物）移入 .trash/；.gitignore 已追加 .trash/
- **全链路联调（2026-09-25 实测 32 项全绿）**：后端 23 项冒烟（公开链路/鉴权/越权/管理聚合/支付闭环/秒杀轮询）+ Agent 搜索工具 + MCP 10 工具（购物 6+管理 4）+ 双前端 devServer 代理链 + SSE 经 7080/7090 代理（Agent 实时对话）全部通过

## P8 百货化改版（2026-09-28）

- **数据（sql/V4__mall_goods.sql）**：删除小米全家桶，8 大百货分类 × 33 件商品（数码家电/家居日用/美妆个护/食品生鲜/服饰鞋包/运动户外/母婴玩具/图书文具）+ 3 张新轮播 + 2 个秒杀场次（801 进行中/802 即将开始）；历史测试订单/购物车/收藏/支付流水已清（用户账号保留）；**执行时必须 mysql --default-character-set=utf8mb4（SQL 内已加 SET_NAMES）**；product_title 列已加宽 varchar(60)
- **图片全本地化**：`shop-frontend/public/imgs/{goods,carousel,promo}/` 共 41 张 SVG（`scripts/gen-goods-svg.py` 生成，可重跑定制）；$target 默认改为 `/`（本地/Nginx 同源托管），不再依赖外部 47.115.85.237 资源服务器；管理端商品列表已加缩略图列
- **管理端导航修复**：ProLayout 菜单默认不与 react-router 联动，`menuItemRender={(item,dom)=><Link to={item.path}>{dom}</Link>}` 接管 + useLocation() 提供当前高亮
- **购物端 xmall 风格改版**：GoodsView 重写为左侧分类栏（渐变高亮）+ 排序条（综合/销量/价格）+ 网格卡片 + 分页；HomeView 重写为动态分类区块（前 4 分类：促销竖图 + 商品网格 + 查看全部直达）；MyList 卡片圆角化 + 修复 category_id→categoryId 名 bug；ES 已重建 33 条，中文语义搜索（坚果/防晒等跨品类命中）正常

- **部署文档**：`deploy/README.md`（中间件清单/环境变量约定/DDL 顺序/构建产物/服务器部署/验证清单/Claude Desktop MCP 接入/FAQ）；Nginx 配置示例 `deploy/nginx-shop.conf.example`（双前端+静态资源+/api 反代+SSE 禁缓冲+剥伪造身份 header 双保险）
- `scripts/svc.sh` 的 ROOT 支持 `SHOP_HOME` 环境变量覆盖——服务器同脚本复用；`SVC_JAVA_OPTS` 传自定义 JVM 参数
- 前端图片前缀 `VUE_APP_IMG_TARGET`（默认老资源服务器 47.115.85.237:3000；生产构建设 `/` 走同源 Nginx 托管 public/）；首页轮播已修复为 `$target + imgPath`（V2 相对路径后漏拼，实为裂图 bug）
- 管理端生产 base 路径按需在 vite.config.js 设置 `base:'/admin/'`（Nginx 同端口目录部署），或用独立域名
- 全链路验收（2026-09-25）：7 服务 Nacos healthy；分类/搜索(降级)/秒杀场/登录/管理统计/越权 403/匿名 401 全通过；双前端 build 通过
- 服务器部署安全三条底线：业务端口不对公网开放（X-User-* 信任边界）、`JWT_SECRET` 生产必换、MCP 8106 加白名单或内网访问

## P9 AI 对话完善：售后闭环 + 用户画像 + 体验修复（2026-09-28）

- **售后闭环（仅退款，order 域 own）**：`aftersale_record` 表（V5__aftersale.sql，手工执行非 Flyway）。状态机 `0待处理→1同意(退款完成)|2已拒绝`（终态，拒绝可重新申请新行）。接口：用户 `POST /order/aftersale/apply`、`GET /order/aftersale/my`；管理 `GET/POST /order/internal/aftersale/page|handle`（**order 端硬校验 ADMIN**，因 /order/internal/** 网关直穿可达，不能只靠"没人知道"防护），admin 聚合 `GET/POST /admin/aftersale/page|handle` 转发。政策：已支付订单(状态1/3)支付后7天内可申请仅退款；待支付引导支付、已取消/超期如实拒绝。`shop.aftersale.apply-window-days` 可配
- **防重复申请不能用 MySQL 唯一键**（无 partial index，终态重申请会撞键）→ `AftersaleServiceImpl.apply` 事务内 `AftersaleMapper.lockOrderRow FOR UPDATE` 锁父订单行 + 检查无 status=0 售后单。审批/拒绝 CAS `WHERE status=0`（仿 payIfPending），双管理员并发只赢一个
- **IdWorker workerId 错开**：AftersaleServiceImpl 用 `new IdWorker(2,1)`，与 OrderServiceImpl(1,1) 不同 workerId，防同毫秒雪花撞号
- **Agent 售后工具**（ShopTools，全走 Feign）：`applyAfterSale(orderId, productId, reason)` / `getMyAfterSales`，new 工具自动进 MCP Server（mcpToolProvider 注册 bean 对象，无需配置）。提示词加售后策略段（先 getMyOrders 定位→如实讲政策→确认理由→申请→保守话术不承诺"马上退款"）+ 空列表友好应对段。实测："每日坚果订单怎么售后"→Agent 查单→表格呈现→确认哪笔+理由
- **无订单友好提示**：`OrderServiceImpl.getOrder` 删除空订单抛 GET_ORDER_NOT_FOUND 的逻辑（原让 Agent 把"无记录"当"查询失败"），改为返回空列表
- **轻量用户画像（长期记忆）**：`UserProfileService`，Redis Hash `chat:profile:u{userId}`（preferredCategories/interestedProducts/styleNote，TTL 30 天）。每轮流结束 `AiChatController.stream()` 的 `doFinally` 异步 `Schedulers.boundedElastic` 调 DeepSeek 提炼（temperature 0.2，门控：登录用户+消息≥10字），异常吞掉不影响对话。注入：`prompt().system(SHOP_PERSONA + profileBlock)`——**request 级 .system() 覆盖 defaultSystem 而非追加**，persona 必须一并传（AgentConfig 抽 `public static final SHOP_PERSONA/ADMIN_PERSONA`）。仅购物 Agent 接入画像，管理 Agent 不做。画像只从 Redis 本地读，最近订单交由工具按需查（避免每轮多一次 Feign）。实测：会话1说"只喝手冲咖啡预算200浅烘阿拉比卡"→Redis 写入三字段→全新会话2问"根据喜好推荐"→直接引用偏好+实搜
- **reactor 线程无 ThreadLocal**：uid 必须在 controller 请求线程捕获进闭包，doFinally/doOnCancel 里禁止读 UserContext
- **体验修复**：①Markdown 渲染——Vue2 用 `marked@4 + DOMPurify`（bot 气泡 v-html 消毒，用户气泡纯文本；vue-markdown 未维护不选用），React 用 `react-markdown + remark-gfm`（渲染为元素不输出 raw HTML 天然防 XSS，禁加 rehype-raw）；②停止生成——前端 `EventSource.close()` 不触发 onerror/done 必须自行 finish 收尾，后端 stream() 加 `doOnCancel/doFinally`；③placeOrder 工具 num 钳制 `Math.min(Math.max(num,1),5)` 防模型幻觉值；④shop-admin 删除死配置（`spring-ai-starter-model-openai` 依赖 + application.yml/local.yml 的 `spring.ai` 块，无任何 ChatModel 代码引用）
- **验收**：售后服务闭环 9 步全绿（下单→支付→申请→重复拦截→分页→拒绝→CAS二次拦截→查结果）；越权(USER 打 handle)被 order 端硬校验拦截；画像提炼+跨会话注入 profileInj=y；后端 `mvn clean package` 全绿；双前端 build 通过
- 新增前端文件：`shop-admin-web/src/pages/Aftersales.jsx`（售后审批页，菜单路由 /aftersales，CustomerServiceOutlined 图标）；后端新增 `AftersaleRecord/AftersaleVo/AftersaleMapper/AftersaleServiceImpl/AftersaleController` + `shop-feign-api/AftersaleApplyRequest` + `shop-chat/profile/UserProfileService`

## 构建与验证

```bash
# 后端全模块构建（改过依赖版本必须 clean）
mvn clean package -DskipTests
# 服务启停（P0 起 7 服务均正常注册 Nacos + 网关转发）
bash scripts/svc.sh start          # 全部启动；可带参数只操作某服务
bash scripts/svc.sh status         # /tmp/shop-logs/<svc>.log 看日志
# 购物端
cd shop-frontend && npm run serve   # 7080，代理 /api → localhost:8080（网关）
# 管理端
cd shop-admin-web && npm run start  # 7090，代理 /api → localhost:8080（P5 建）
# 中间件：MySQL/Redis/RabbitMQ 本机；Nacos/ES 服务器 8.130.22.3（P0 已验证注册健康）
```

## 实施阶段（详细方案见 plans/xmall-agent-mcp-agent-agent-misty-marble.md）

P0 骨架+版本冒烟 → P1 JWT鉴权 → P2 秒杀闭环 → P3 ES搜索 → P4 Agent/MCP → P5 admin服务+React端 → P6 支付 → P7 部署打磨

每阶段结束要有可验证产出（构建通过 + 核心链路 curl/页面验证），测试随代码走。