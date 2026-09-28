#!/bin/bash
# P2 秒杀闭环并发验证脚本（本地，shop 服务须在运行）
# 前置：gateway:8080 全链路可用；执行后所有结论输出到 /tmp/p2-result.txt
set -u
GW=http://localhost:8080
OUT=/tmp/p2-result.txt
: > $OUT

log() { echo "$@" >> $OUT; }

# 1) 造进行中的秒杀场次（库存 10）
mysql -uroot -pshuozhi123456 shopmanagement -N -e "
INSERT INTO seckill_time (start_time, end_time) VALUES ((UNIX_TIMESTAMP()-3600)*1000, (UNIX_TIMESTAMP()+7200)*1000);
SET @tid = LAST_INSERT_ID();
INSERT INTO seckill_product (seckill_price, seckill_stock, product_id, time_id) VALUES (999.0, 10, 2, @tid);
SELECT seckill_id FROM seckill_product WHERE time_id = @tid;" 2>/dev/null | tail -1 > /tmp/p2-seckillid
SECKILL_ID=$(cat /tmp/p2-seckillid)
log "== 场次已建 seckillId=$SECKILL_ID (库存10) =="

# 2) warm Redis 缓存（getSeckill 会建 hash + stock key）
curl -s "$GW/seckill/product/$SECKILL_ID" >> $OUT
log ""

# 3) 批量注册 40 个测试用户
for i in $(seq 1 40); do
  curl -s -m 5 -X POST $GW/user/register -H "Content-Type: application/json" \
    -d "{\"username\":\"bkt$i\",\"password\":\"Test123456\"}" > /dev/null
done
log "== 已注册 bkt1..bkt40 =="

# 4) 40 个用户并发抢购（auth 用 cookie jar 独立保存）
mkdir -p /tmp/p2-cookies
for i in $(seq 1 40); do
  curl -s -m 5 -X POST $GW/user/login -H "Content-Type: application/json" \
    -d "{\"username\":\"bkt$i\",\"password\":\"Test123456\"}" -c /tmp/p2-cookies/c$i.txt > /dev/null
done
log "== 40 个用户登录完成，开始并发抢购 =="

seq 1 40 | xargs -P 20 -I{} curl -s -m 10 -X POST -b /tmp/p2-cookies/c{}.txt "$GW/seckill/product/seckill/$SECKILL_ID" | sort | uniq -c >> $OUT

# 5) 等消费完成
sleep 10

# 6) 结果验证
log "== 秒杀订单数（期望 10，status=0 待支付）=="
mysql -uroot -pshuozhi123456 shopmanagement -N -e "SELECT COUNT(*) FROM \`order\` WHERE seckill_id=$SECKILL_ID;" 2>/dev/null >> $OUT
log "== 消息表状态分布（期望 10条 CONSUMED）=="
mysql -uroot -pshuozhi123456 shopmanagement -N -e "SELECT status, COUNT(*) FROM seckill_message_record WHERE seckill_id='$SECKILL_ID' GROUP BY status;" 2>/dev/null >> $OUT
log "== DB 剩余库存（期望 0）=="
mysql -uroot -pshuozhi123456 shopmanagement -N -e "SELECT seckill_stock FROM seckill_product WHERE seckill_id=$SECKILL_ID;" 2>/dev/null >> $OUT
log "== 超卖断言：订单数>10 ? =="
ORDERS=$(mysql -uroot -pshuozhi123456 shopmanagement -N -e "SELECT COUNT(*) FROM \`order\` WHERE seckill_id=$SECKILL_ID;" 2>/dev/null)
if [ "$ORDERS" -gt 10 ]; then log "!!!! 超卖：$ORDERS > 10"; else log "无超卖（订单数=$ORDERS）"; fi
log "== P2 VERIFY DONE =="