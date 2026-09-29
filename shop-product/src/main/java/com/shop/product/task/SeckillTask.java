package com.shop.product.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.util.RedisKey;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.mapper.SeckillProductMapper;
import com.shop.product.mapper.SeckillTimeMapper;
import com.shop.product.pojo.SeckillProduct;
import com.shop.product.pojo.SeckillTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀活动生成任务（迁移自单体 SeckillTask）。
 * P2：Redis setnx 分布式锁——多实例部署时防止重复清表/重复生成。
 * 逻辑：每日 15:00 清空并生成当天自整点起 12 个偶数小时场次 × 每场 15 个商品（库存 100）。
 */
@Component
public class SeckillTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillTask.class);

    @Autowired
    private SeckillTimeMapper seckillTimeMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 0 15 * * ?")
    public void execute() {
        // 分布式锁：30s 过期兜底（任务本身（24场×15商品）耗时应远小于此）
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(RedisKey.LOCK_SECKILL_TASK, "1", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            log.info("[SeckillTask] 其它实例已在执行，本实例跳过");
            return;
        }
        try {
            doExecute();
        } finally {
            stringRedisTemplate.delete(RedisKey.LOCK_SECKILL_TASK);
        }
    }

    private void doExecute() {
        List<Integer> productIds = productMapper.selectIds();
        Date time = getDate();

        // P1 修复：手动场（manual）整体保留——场次与其商品都不删；只清 auto 场及其商品。
        // 原实现全删 seckill_time + seckill_product，管理员手动建的活动次日下午 3 点被无声清空。
        List<SeckillTime> manualTimes = seckillTimeMapper.selectList(
                new LambdaQueryWrapper<SeckillTime>().eq(SeckillTime::getSource, "manual"));
        List<Integer> manualTimeIds = manualTimes.stream().map(SeckillTime::getTimeId).toList();
        if (manualTimeIds.isEmpty()) {
            seckillTimeMapper.delete(new LambdaQueryWrapper<>());
            seckillProductMapper.delete(new LambdaQueryWrapper<>());
        } else {
            seckillTimeMapper.delete(new LambdaQueryWrapper<SeckillTime>()
                    .notIn(SeckillTime::getTimeId, manualTimeIds));
            seckillProductMapper.delete(new LambdaQueryWrapper<SeckillProduct>()
                    .notIn(SeckillProduct::getTimeId, manualTimeIds));
        }

        for (int i = 1; i < 24; i += 2) {
            long startTime = time.getTime() / 1000 * 1000 + 1000L * 60 * 60 * i;
            long endTime = startTime + 1000L * 60 * 60;

            SeckillTime seckillTime = new SeckillTime();
            seckillTime.setStartTime(startTime);
            seckillTime.setEndTime(endTime);
            seckillTime.setSource("auto");
            seckillTimeMapper.insert(seckillTime);

            // 随机选择 15 个商品
            Set<Integer> randomProductIds = new HashSet<>();
            Random random = new Random();
            while (randomProductIds.size() < 15 && !productIds.isEmpty()) {
                randomProductIds.add(productIds.get(random.nextInt(productIds.size())));
            }
            List<Integer> selectedProductIds = new ArrayList<>(randomProductIds);

            List<SeckillProduct> seckillProducts = new ArrayList<>();
            for (Integer productId : selectedProductIds) {
                SeckillProduct seckillProduct = new SeckillProduct();
                seckillProduct.setSeckillPrice(1000.0);
                seckillProduct.setSeckillStock(100);
                seckillProduct.setProductId(productId);
                seckillProduct.setTimeId(seckillTime.getTimeId());
                seckillProducts.add(seckillProduct);
            }
            seckillProducts.forEach(seckillProductMapper::insert);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            log.info("[SeckillTask] 完成时间段 {} 的秒杀商品配置", i);
        }
        log.info("[SeckillTask] 当日秒杀活动生成完毕（保留手动场 {} 个）", manualTimes.size());
    }

    private Date getDate() {
        Calendar ca = Calendar.getInstance();
        ca.set(Calendar.MINUTE, 0);
        ca.set(Calendar.SECOND, 0);
        return ca.getTime();
    }
}