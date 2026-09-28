-- P2 秒杀闭环测试数据：造一个"进行中"场次（当前时间前1h ~ 后2h）+ 库存10 的秒杀商品
USE shopmanagement;
INSERT INTO seckill_time (start_time, end_time)
VALUES ((UNIX_TIMESTAMP() - 3600) * 1000, (UNIX_TIMESTAMP() + 7200) * 1000);
SET @tid = LAST_INSERT_ID();
INSERT INTO seckill_product (seckill_price, seckill_stock, product_id, time_id) VALUES (999.0, 10, 2, @tid);
SELECT seckill_id, seckill_stock, time_id FROM seckill_product WHERE time_id = @tid;