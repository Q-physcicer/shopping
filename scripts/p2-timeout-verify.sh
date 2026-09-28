#!/bin/bash
# P2 超时取消链路验证（需要在 order 以 15s TTL 运行时执行）
set -u
OUT=/tmp/p2-timeout-result.txt
: > $OUT
log() { echo "$@" >> $OUT; }

# 1) 清空 timeout 队列（避免历史 30min TTL 消息队头堵塞）
curl -s -X DELETE -u guest:guest "http://localhost:15672/api/queues/%2F/order.pay.timeout.queue/contents"
log "== timeout 队列已清空 =="

# 2) bkt1 下一单
rm -f /tmp/bkt1.cookie
curl -s -m 5 -X POST http://localhost:8080/user/login -H "Content-Type: application/json" \
  -d '{"username":"bkt1","password":"Test123456"}' -c /tmp/bkt1.cookie > /dev/null
curl -s -m 8 -X POST -b /tmp/bkt1.cookie http://localhost:8080/cart/product/3 > /dev/null
mysql -uroot -pshuozhi123456 shopmanagement -N -e "SELECT product_num FROM product WHERE product_id=3;" 2>/dev/null > /tmp/stock_before
log "下单前库存: $(cat /tmp/stock_before)"
R=$(curl -s -m 8 -X POST -b /tmp/bkt1.cookie http://localhost:8080/order -H "Content-Type: application/json" -d '[{"productId":3,"num":1,"price":2599.0}]')
log "下单响应: $R"

# 3) 等 30 秒（15s TTL + 死信消费）
sleep 30

# 4) 断言
log "== 30s 后库存（期望回滚回下单前值）=="
mysql -uroot -pshuozhi123456 shopmanagement -N -e "SELECT product_num FROM product WHERE product_id=3;" 2>/dev/null >> $OUT
log "== bkt1 最新普通订单状态（期望 2=已取消）=="
mysql -uroot -pshuozhi123456 shopmanagement -e "SELECT order_id, status FROM \`order\` WHERE user_id=(SELECT user_id FROM user WHERE username='bkt1') AND seckill_id IS NULL ORDER BY id DESC LIMIT 1;" 2>/dev/null >> $OUT
log "== 超时取消日志 =="
grep "TimeoutCancel\|OrderTimeout" /tmp/shop-logs/order.log | tail -3 >> $OUT
log "== P2 TIMEOUT VERIFY DONE =="