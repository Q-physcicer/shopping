#!/bin/bash
# 星选商城微服务启停脚本（Linux / macOS）
#
# 用法：bash svc.sh start|stop|status|restart [目标 ...]
#   目标 = 7 个后端服务：gateway user product cart order admin chat
#          fe   购物端前端（shop-frontend，http://localhost:7080）
#          web  管理端前端（shop-admin-web，http://localhost:7090）
#   不带目标 = 全部 7 个后端服务（前端需显式指定 fe / web）
# 示例：
#   bash svc.sh start              # 启动全部后端服务
#   bash svc.sh start fe web       # 启动两个前端
#   bash svc.sh restart product    # 重启单个服务
#
# 环境变量：
#   SVC_JAVA_OPTS  附加 JVM 参数，如 SVC_JAVA_OPTS="-Dshop.order.pay-timeout-ms=15000"
#   SHOP_HOME      项目根目录（默认取脚本所在目录，无需设置）
#   LOG_DIR        日志目录（默认 <项目根>/logs，已在 .gitignore 忽略）
#
# 注意：
#   - 服务依赖 Nacos：启动前先执行 NACOS_ADDR=你的Nacos地址:8848 bash nacos-config-upload.sh
#   - restart 中 stop 后 JVM 优雅退出需要几秒，脚本固定等待 10s 再启动，请勿缩短
set -u

ROOT=${SHOP_HOME:-$(cd "$(dirname "$0")" && pwd)}
LOG_DIR=${LOG_DIR:-$ROOT/logs}
mkdir -p "$LOG_DIR"

SERVICES=(gateway user product cart order admin chat)
JAVA_OPTS=${SVC_JAVA_OPTS:-}

# ---------- 后端服务 ----------

start_one() {
  local svc=$1
  local jar="$ROOT/shop-$svc/target/shop-$svc-1.0.0-SNAPSHOT.jar"
  if [ ! -f "$jar" ]; then echo "[ERR] $svc jar 不存在: $jar（先执行 mvn clean package -DskipTests）"; return 1; fi
  if pgrep -f "shop-$svc-1.0.0-SNAPSHOT.jar" >/dev/null 2>&1; then
    echo "[SKIP] $svc 已在运行"; return 0
  fi
  nohup java -Xms256m -Xmx512m $JAVA_OPTS -jar "$jar" > "$LOG_DIR/$svc.log" 2>&1 &
  echo "[START] $svc pid=$!  日志: $LOG_DIR/$svc.log"
}

stop_one() {
  local svc=$1 pid
  pid=$(pgrep -f "shop-$svc-1.0.0-SNAPSHOT.jar" | head -1)
  if [ -z "${pid:-}" ]; then echo "[SKIP] $svc 未在运行"; return 0; fi
  kill "$pid" && echo "[STOP] $svc pid=$pid（JVM 优雅退出需几秒）"
}

status_one() {
  local svc=$1
  if pgrep -f "shop-$svc-1.0.0-SNAPSHOT.jar" >/dev/null 2>&1; then
    echo "[UP]   shop-$svc"
  else
    echo "[DOWN] shop-$svc"
  fi
}

# ---------- 前端 ----------

start_fe() {
  if pgrep -f "shop-frontend.*vue-cli-service" >/dev/null 2>&1; then
    echo "[SKIP] fe 已在运行"; return 0
  fi
  ( cd "$ROOT/shop-frontend" && nohup npm run serve > "$LOG_DIR/fe.log" 2>&1 & )
  echo "[START] 购物端(7080)  日志: $LOG_DIR/fe.log"
}

stop_fe() {
  local pid
  pid=$(pgrep -f "shop-frontend.*vue-cli-service" | head -1)
  if [ -z "${pid:-}" ]; then echo "[SKIP] fe 未在运行"; return 0; fi
  kill "$pid" && echo "[STOP] 购物端 pid=$pid"
}

status_fe() {
  if pgrep -f "shop-frontend.*vue-cli-service" >/dev/null 2>&1; then
    echo "[UP]   购物端前端 shop-frontend (7080)"
  else
    echo "[DOWN] 购物端前端 shop-frontend (7080)"
  fi
}

start_web() {
  if pgrep -f "shop-admin-web.*vite" >/dev/null 2>&1; then
    echo "[SKIP] web 已在运行"; return 0
  fi
  ( cd "$ROOT/shop-admin-web" && nohup npm run dev > "$LOG_DIR/web.log" 2>&1 & )
  echo "[START] 管理端(7090)  日志: $LOG_DIR/web.log"
}

stop_web() {
  local pid
  pid=$(pgrep -f "shop-admin-web.*vite" | head -1)
  if [ -z "${pid:-}" ]; then echo "[SKIP] web 未在运行"; return 0; fi
  kill "$pid" && echo "[STOP] 管理端 pid=$pid"
}

status_web() {
  if pgrep -f "shop-admin-web.*vite" >/dev/null 2>&1; then
    echo "[UP]   管理端前端 shop-admin-web (7090)"
  else
    echo "[DOWN] 管理端前端 shop-admin-web (7090)"
  fi
}

# ---------- 分发 ----------

run_action() {
  local action=$1 target=$2
  case "$target" in
    fe)  "${action}_fe" ;;
    web) "${action}_web" ;;
    gateway|user|product|cart|order|admin|chat) "${action}_one" "$target" ;;
    *) echo "[ERR] 未知目标: $target（可选: ${SERVICES[*]} fe web）"; return 1 ;;
  esac
}

ACTION=${1:-}
shift || true
TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then TARGETS=("${SERVICES[@]}"); fi

case "$ACTION" in
  start)
    for t in "${TARGETS[@]}"; do run_action start "$t"; done ;;
  stop)
    for t in "${TARGETS[@]}"; do run_action stop "$t"; done ;;
  status)
    for t in "${TARGETS[@]}"; do run_action status "$t"; done ;;
  restart)
    for t in "${TARGETS[@]}"; do run_action stop "$t"; done
    echo "... 等待 10s 让进程退出 ..."
    sleep 10
    for t in "${TARGETS[@]}"; do run_action start "$t"; done ;;
  *)
    echo "用法: bash svc.sh start|stop|status|restart [gateway|user|product|cart|order|admin|chat|fe|web]"
    exit 1 ;;
esac