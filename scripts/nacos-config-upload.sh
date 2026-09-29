#!/bin/bash
# 将 deploy/nacos/*.yml 上传到 Nacos 配置中心（dataId=文件名，group=SHOP，type=yaml）
# 该 Nacos 多项目共用（8.130.22.3:8848）——只操作 SHOP 组内自己的 dataId（均 shop- 前缀），
# 绝不触碰 DEFAULT_GROUP 内他项目配置（application-common.yaml / shared-jwt.yaml / agent-*-prompt.txt 等）。
# 用法：
#   bash scripts/nacos-config-upload.sh
#   本地 compose Nacos：NACOS_ADDR=localhost:8848 bash scripts/nacos-config-upload.sh
#   服务端启用鉴权时：export NACOS_USERNAME=xxx NACOS_PASSWORD=xxx（脚本先 login 换 accessToken）
# 幂等：同一 dataId 重复上传即整体覆盖，可反复执行
set -u

NACOS_ADDR=${NACOS_ADDR:-8.130.22.3:8848}
NACOS_GROUP=${NACOS_GROUP:-SHOP}
DIR=$(cd "$(dirname "$0")/../deploy/nacos" && pwd)

if [ ! -d "$DIR" ]; then
  echo "[ERR] 目录不存在: $DIR"
  exit 1
fi

# 鉴权模式：提供账密则 login 换 accessToken；否则裸传（服务端未开鉴权）
login() {
  if [ -z "${NACOS_USERNAME:-}" ] || [ -z "${NACOS_PASSWORD:-}" ]; then
    echo ""
    return
  fi
  curl -s -m 10 -X POST "http://$NACOS_ADDR/nacos/v1/auth/login" \
    -d "username=$NACOS_USERNAME" -d "password=$NACOS_PASSWORD" \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

TOKEN=$(login)

fail=0
shopt -s nullglob
files=("$DIR"/*.yml)
if [ ${#files[@]} -eq 0 ]; then
  echo "[ERR] $DIR 下没有 .yml 文件"
  exit 1
fi

for f in "${files[@]}"; do
  dataId=$(basename "$f")
  printf "[UPLOAD] %-24s → %s (group %s) ... " "$dataId" "$NACOS_ADDR" "$NACOS_GROUP"
  args=(-s -m 20 -X POST "http://$NACOS_ADDR/nacos/v1/cs/configs"
        -d "dataId=$dataId" -d "group=$NACOS_GROUP" -d "type=yaml"
        --data-urlencode "content@$f")
  [ -n "$TOKEN" ] && args+=(-d "accessToken=$TOKEN")
  resp=$(curl "${args[@]}")
  if [ "$resp" = "true" ]; then
    echo "[OK]"
  else
    echo "[FAIL] resp=$resp"
    echo "        403/未授权 → export NACOS_USERNAME=... NACOS_PASSWORD=... 后重跑"
    fail=1
  fi
done

if [ $fail -eq 0 ]; then
  echo "== 全部上传完成（group=$NACOS_GROUP）=="
fi
exit $fail
