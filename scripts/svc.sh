#!/bin/bash
# 微服务启停脚本（本机开发）
# 用法：svc.sh start|stop|status [服务名(可选:gateway user product cart order admin chat)]
set -u

ROOT=${SHOP_HOME:-/Users/shuozhi/IdeaProjects/shopping}   # 服务器部署用 SHOP_HOME 覆盖
LOG_DIR=/tmp/shop-logs
mkdir -p "$LOG_DIR"

SERVICES=(gateway user product cart order admin chat)

# 可选自定义 JVM 参数：SVC_JAVA_OPTS="-Dshop.order.pay-timeout-ms=15000" bash svc.sh start order
JAVA_OPTS=${SVC_JAVA_OPTS:-}

start_one() {
  local svc=$1
  local jar="$ROOT/shop-$svc/target/shop-$svc-1.0.0-SNAPSHOT.jar"
  if [ ! -f "$jar" ]; then echo "[ERR] $svc jar 不存在: $jar"; return 1; fi
  if pgrep -f "shop-$svc-1.0.0-SNAPSHOT.jar" >/dev/null 2>&1; then
    echo "[SKIP] $svc 已在运行"; return 0
  fi
  nohup java -Xms256m -Xmx512m $JAVA_OPTS -jar "$jar" > "$LOG_DIR/$svc.log" 2>&1 &
  echo "[START] $svc pid=$!"
}

stop_one() {
  local svc=$1
  local pid
  pid=$(pgrep -f "shop-$svc-1.0.0-SNAPSHOT.jar" | head -1)
  if [ -z "${pid:-}" ]; then echo "[SKIP] $svc 未在运行"; return 0; fi
  kill "$pid" && echo "[STOP] $svc pid=$pid"
}

status_one() {
  local svc=$1
  if pgrep -f "shop-$svc-1.0.0-SNAPSHOT.jar" >/dev/null 2>&1; then
    echo "[UP]   shop-$svc"
  else
    echo "[DOWN] shop-$svc"
  fi
}

ACTION=${1:-}
shift || true
TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then TARGETS=("${SERVICES[@]}"); fi

case "$ACTION" in
  start) for t in "${TARGETS[@]}"; do start_one "$t"; done ;;
  stop)  for t in "${TARGETS[@]}"; do stop_one "$t"; done ;;
  status) for t in "${TARGETS[@]}"; do status_one "$t"; done ;;
  restart) for t in "${TARGETS[@]}"; do stop_one "$t"; done; sleep 2; for t in "${TARGETS[@]}"; do start_one "$t"; done ;;
  *) echo "用法: svc.sh start|stop|status|restart [gateway|user|product|cart|order|admin|chat]"; exit 1 ;;
esac