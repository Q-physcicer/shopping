package com.shop.product.controller;

/**
 * Sentinel 资源名统一管理（SentinelRulesConfig 与 @SentinelResource 引用同一常量）
 */
public final class SentinelResourceNames {

    /** 秒杀抢购入口 */
    public static final String SECKILL = "seckillProduct";

    private SentinelResourceNames() {
    }
}