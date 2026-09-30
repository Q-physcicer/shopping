# 星选商城 · 分布式部署指南

> 架构：Spring Cloud Alibaba 微服务（7 服务 + Gateway）+ Vue2 购物端 + React 管理端 + AI Agent/MCP

## 一、服务器中间件需求清单

> 中间件可与应用同机部署，也可使用独立实例——地址与凭据全部通过环境变量注入（见第二节）。

| 中间件 | 版本建议 | 用途 | 端口 | 内存预估 |
|---|---|---|---|---|
| JDK | OpenJDK **17** (Temurin) | 运行 7 个 Java 服务 | - | - |
| MySQL | 8.0+ | 业务库 shop | 3306 | 1 GB |
| Redis | 6.2+ / 7.x | 秒杀预扣/JWT 黑名单/Agent 会话 | 6379 | 512 MB |
| RabbitMQ | 3.13+ + management 插件 | 削峰/超时取消/ES 同步（**无需延迟插件**，TTL+死信原生实现） | 5672 / 15672 | 512 MB |
| Nacos | 2.x standalone | 注册中心 + 配置中心（group SHOP） | 8848 / 9848 | 768 MB |
| Elasticsearch | 8.x + **analysis-ik 同版本插件** | 商品搜索（未装即自动降级 MySQL，不阻塞） | 9200 | 堆 512m 即可 |
| Nginx | 1.24+ | 双前端静态 + /api 反代 | 80 | 256 MB |
| Node.js | 18 LTS（仅构建前端用） | npm run build | - | - |

**服务器建议配置：4C / 16GB / 60GB SSD**（最低 2C/8G 可跑）。7 个 JVM 各 `-Xmx512m` ≈ 3.5G。

ES 说明：需安装 analysis-ik 插件后启用（中文分词/高亮/排序，启动自动建索引+全量导入）；未装 ES 的环境自动降级 MySQL LIKE，不阻塞运行。启用方式：`ES_ENABLED=true`（或 `SVC_JAVA_OPTS=-Dshop.search.es-enabled=true`）启动 product。

## 二、环境配置约定

后端所有敏感/环境差异配置都走环境变量（各服务 application.yml 已内置默认值）：

| 环境变量 | 说明 | 默认 |
|---|---|---|
| `NACOS_ADDR` | Nacos 地址 | **空（必填）**，如 `localhost:8848` 或 `你的Nacos服务器地址:8848` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 账号（**仅服务端开启鉴权时**需注入；注册与配置同用） | 空 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL（部署时为服务器实例） | localhost:3306/shop / root / 空 |
| `REDIS_HOST` / `REDIS_PORT` | Redis | localhost / 6379 |
| `MQ_HOST` / `MQ_PORT` / `MQ_USERNAME` / `MQ_PASSWORD` | RabbitMQ | localhost / 5672 / guest / guest |
| `ES_URI` / `ES_ENABLED` | ES 地址与开关 | 空 / **false**（如 `http://你的ES服务器地址:9200` / true） |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（shop-chat 服务，不配则 AI 对话不可用） | demo |
| `JWT_SECRET` | HS256 密钥（生产必须改，≥32 字符随机串） | dev 占位值 |
| `SPRING_PROFILES_ACTIVE` | Spring profile | local（生产用 server 并提供对应 yml，或全 env 注入） |

> 本机开发：数据库/Redis/MQ 密码放各服务 `application-local.yml`（已 gitignore）；服务器部署：通过 systemd `Environment=` 或 `/etc/shop.env` 注入后启动，**不要**把真实密钥写入仓库。

## 三、数据库初始化（单脚本全量）

```bash
mysql -u root -p --default-character-set=utf8mb4 < sql/shop.sql
# 15 张表最终态 + 种子数据（8 分类/33 商品/3 轮播/2 秒杀场次/20 用户含 admin/admin123）
# DROP+CREATE 语义，可重复执行（重跑即重建全部表；⚠️ 会清空业务数据）
```

> shop.sql 为全量初始化脚本，新环境只执行这一个文件即可；本地个性化连接信息可写各服务 `application-local.yml`（已 gitignore）覆盖。

## 三·五、Nacos 配置中心（bootstrap 模式）

各服务配 `bootstrap.yml`（spring-cloud-starter-bootstrap + nacos-config 依赖）拉取公共配置；`application-local.yml` 本地覆盖机制原样保留。

**数据约定**：本项目全部配置放独立的 `SHOP` 组、dataId `shop-` 前缀（目前仅 `shop-common.yml`）；若使用与其他项目共享的 Nacos，**只操作 SHOP 组内自己的 dataId，绝不动他组/他项目配置**。

**改动公共配置**：编辑 `deploy/nacos/shop-common.yml`（git 跟踪的源文件）→ 重跑 `bash nacos-config-upload.sh` → 重启各服务（配置中心是**分发**不是热更新：redis/mq/mybatis-plus 工厂早就实例化完，refresh:true 只对将来 @RefreshScope Bean 生效）。

**上传脚本**：需显式指定 Nacos 地址——`NACOS_ADDR=你的Nacos地址:8848 bash nacos-config-upload.sh`；服务端开鉴权时 `export NACOS_USERNAME=.. NACOS_PASSWORD=..`（自动 login 换 accessToken）。幂等可重复执行。

**优先级三条铁律**（bootstrap 模式下 Nacos 远端 > JVM -D > env > 本地 yml **含 local 覆盖**——远端对本地是键级完胜）：
1. 敏感值（密码/密钥）**禁止**上 Nacos
2. 需要 `application-local.yml` 覆盖的键（datasource.url 等）**禁止**上 Nacos——否则本地的覆盖配置会被远端打死
3. 需要运维 env/`-D` 覆盖的键一律写 `${ENV:default}` 占位符——占位符在子上下文解析，env 注入照常生效

**fail-fast**：bootstrap.yml 已设 `spring.cloud.nacos.config.fail-fast: true`——Nacos 不可达时服务**拒启**而非静默丢公共配置（mybatis-plus id-type=auto 丢失会导致雪花 id 写 int 自增列的数据事故）。代价：单机 Nacos 故障 = 全服务无法启动，属有意取舍。

## 四、构建产物

```bash
# 后端（改过依赖必须 clean）
mvn clean package -DskipTests
# → shop-{gateway,user,product,cart,order,admin,chat}/target/*.jar

# 购物端
cd shop-frontend && npm install
VUE_APP_IMG_TARGET=/ npm run build        # 生产走同源静态资源
# → dist/

# 管理端
cd shop-admin-web && npm install && npm run build
# → dist/
```

## 五、服务器部署

### 1) 后端 7 服务

```bash
# 假设部署在 /opt/shop（脚本默认取自身所在目录为项目根，跨机部署可用 SHOP_HOME 覆盖）
bash svc.sh start     # 全部启动；日志 <项目根>/logs/<svc>.log
bash svc.sh status
# 单服务：bash svc.sh start|stop|restart order
# 前端：bash svc.sh start fe web   （fe=购物端 7080，web=管理端 7090）
# 自定义 JVM 参数：SVC_JAVA_OPTS="-Xmx512m" bash svc.sh start chat
# Windows 服务器对应命令：svc.bat start|stop|status|restart [服务名|fe|web]
```

环境变量注入示例（systemd）：

```ini
# /etc/systemd/system/shop-order.service（其余服务同理）
[Unit]
Description=shop-order
After=network.target

[Service]
WorkingDirectory=/opt/shop
Environment=DB_PASSWORD=你的密码
Environment=JWT_SECRET=请换成32位以上随机串
Environment=MQ_HOST=127.0.0.1
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/shop/shop-order/target/shop-order-1.0.0-SNAPSHOT.jar
Restart=always
User=apps

[Install]
WantedBy=multi-user.target
```

**安全要求**：
- 业务端口 8101-8106 与网关 8080 **不得对公网开放**（防火墙只放行 80/443 与维护端口）；X-User-* header 信任边界依赖网络隔离
- MCP Server（8106 /sse）如需给外部 AI 客户端使用，请置于内网或加 IP 白名单/vpn；当前 MCP 通道无登录态，写类工具会被后端"未登录"拦截
- **角色变更的 token 滞后窗口**：网关只校验 JWT 内 role 不回查数据库——修改用户角色后，其已签发 token 在剩余有效期（默认 7 天）内仍按旧角色放行（提权同理存在最长 7 天的延迟生效）。管理账号变动后建议要求对方重新登录；更高安全等级需引入 token version（登出/改角色时踢全部会话）
- **internal 接口**已双拦（网关 403 + 服务端 ADMIN 硬校验），但业务端口暴露仍属失守——网络隔离仍是第一道防线

### 2) 前端 + Nginx

```bash
# 产物放到服务器（server 换成你的服务器地址）
scp -r shop-frontend/dist/*  server:/opt/shop/shop-frontend-dist/
scp -r shop-admin-web/dist/* server:/opt/shop/shop-admin-dist/
scp -r shop-frontend/public  server:/opt/shop/public/     # 图片等静态资源
# 参照 deploy/nginx-shop.conf.example 配置 Nginx 后 reload
```

## 六、验证清单（部署后在服务器本机 curl 网关）

```bash
GW=http://127.0.0.1:8080
NACOS=http://你的Nacos服务器地址:8848
# 注册中心：7 个服务全部健康（Nacos 控制台或 API 可查）
curl -s "$NACOS/nacos/v1/ns/instance/list?serviceName=shop-gateway"

# 公开只读链路
curl -s $GW/category | head -c 200
curl -s $GW/resources/carousel | head -c 200
# 登录态链路（拿 cookie）
curl -s -X POST $GW/user/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' -c cookie.txt
curl -s -b cookie.txt $GW/admin/stats/overview | head -c 300     # 管理端统计（ADMIN）
# 搜索（MySQL 降级模式正常返回 engine=mysql）
curl -s -G $GW/product/search --data-urlencode "keyword=坚果" | head -c 200
# AI Agent SSE（登录态）
curl -s -N -b cookie.txt -G $GW/chat/stream --data-urlencode "message=你好" | head -5
# MCP（8106 直连）：tools/list 应返回 12 个工具
curl -s -N http://127.0.0.1:8106/sse | head -3
```

前端验收：购物端首页/搜索/秒杀/购物车/下单→收银台支付/AI 挂件代购；管理端 Dashboard 图表/商品上架→购物端可搜/订单/用户/AI 助手。

## 七、Claude Desktop 等外部 MCP 客户端接入

```json
{
  "mcpServers": {
    "xm-shop": {
      "url": "http://你的服务器地址:8106/sse"
    }
  }
}
```

接入后可 discover 全部 12 个工具——购物：searchProducts / getProductDetail / addToCart / getMyCart / placeOrder / getMyOrders / applyAfterSale / getMyAfterSales；管理：adminSearchProducts / publishProduct / updateProduct / getStats。写类工具因 MCP 通道无登录态会被服务端拦截（安全设计）。

## 八、常见问题

| 症状 | 原因/处理 |
|---|---|
| 服务起不来报 `factoryBeanObjectType` | mybatis-spring 被降级：父 POM 已锁 3.0.4，勿移除；改版本后必须 `mvn clean package` |
| 秒杀压测后订单没落库 | 看 <项目根>/logs/order.log 的 SeckillOrderListener；消息表 status 卡 SENT=消费失败，重试 3 次后死信回滚 |
| 演示短 TTL 超时不生效 | per-message TTL 有**队头堵塞**：先清空 `order.pay.timeout.queue`（15672 管理台/API）再下新单 |
| ES 开了但搜索仍 engine=mysql | 启动时 ES 未就绪会熔断并记内存标记；ES 修好后调 `POST /product/internal/es/rebuild` 或重启 product |
| SSE 断流/token 不出来 | Nginx 需加 `proxy_buffering off`；网关 response-timeout 已 180s；勿在 /api 路由上加压缩/改写 filter |
| Agent 说"未登录"但用户已登录 | 业务端口被外部直连绕过网关（header 无身份）；或 MCP 外部通道（预期行为） |