package com.shop.common.util;

/**
 * @Auther: wdd
 * @Date: 2020/8/23 11:54
 * @Description:
 */
public class RedisKey {
    public final static String SECKILL_PRODUCT_LIST = "seckill:product:list:";
    public final static String SECKILL_PRODUCT = "seckill:product:id:";
    public final static String SECKILL_PRODUCT_STOCK = "seckill:product:stock:id:";
    public final static String SECKILL_PRODUCT_USER_LIST = "seckill:product:user:list";
    public final static String SECKILL_PRODUCT_USER_SET = "seckill:product:user:set:";   // P2：SADD 原子防重复抢购
    public final static String SECKILL_RABBITMQ_ID = "seckill:rabbitmq:id";
    public final static String LOCK_SECKILL_TASK = "lock:seckill:task";                  // P2：SeckillTask 分布式锁
}
