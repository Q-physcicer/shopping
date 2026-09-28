package com.shop.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.product.mapper.SeckillMessageRecordMapper;
import com.shop.product.mapper.SeckillProductMapper;
import com.shop.product.mapper.SeckillTimeMapper;
import com.shop.product.pojo.SeckillMessageRecord;
import com.shop.product.pojo.SeckillProduct;
import com.shop.product.pojo.SeckillTime;
import com.shop.common.util.BeanUtil;
import com.shop.common.util.RedisKey;
import com.shop.product.vo.SeckillProductVo;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @Description: 秒杀商品服务实现类
 */
@Service
public class SeckillProductServiceImpl {

        @Autowired
        private RedisTemplate redisTemplate;
        @Autowired
        private StringRedisTemplate stringRedisTemplate;
        @Autowired
        private RabbitTemplate rabbitTemplate;
        @Autowired
        private SeckillProductMapper seckillProductMapper;
        @Autowired
        private SeckillTimeMapper seckillTimeMapper;
        @Autowired
        private SeckillMessageRecordMapper messageRecordMapper;

        @Autowired
        ObjectMapper objectMapper;

        private HashMap<String, Boolean> localOverMap = new HashMap<>();

        @Transactional
        public List<SeckillProductVo> getProduct(String timeId) {

                // 先查看缓存，是否有列表
                List<SeckillProductVo> seckillProductVos = redisTemplate.opsForList().range(
                        RedisKey.SECKILL_PRODUCT_LIST + timeId, 0, -1
                );

                if (!ArrayUtils.isEmpty(seckillProductVos.toArray())) {
                        return seckillProductVos;
                }

                // 缓存没有，再从数据库中获取，添加到缓存
                seckillProductVos = seckillProductMapper.getSeckillProductVos(timeId, new Date().getTime());
                if (!seckillProductVos.isEmpty()) {
                        // 批量存入缓存并设置过期时间
                        redisTemplate.opsForList().leftPushAll(RedisKey.SECKILL_PRODUCT_LIST + timeId, seckillProductVos);
                        // 设置过期时间
                        long ttl = seckillProductVos.get(0).getEndTime() - System.currentTimeMillis();
                        redisTemplate.expire(RedisKey.SECKILL_PRODUCT_LIST + timeId, ttl, TimeUnit.MILLISECONDS);
                } else {
                        // 秒杀商品过期或不存在
                        throw new XmException(ExceptionEnum.GET_SECKILL_NOT_FOUND);
                }

                return seckillProductVos;
        }


        /**
         * 获取当前时间的整点
         *
         * @return
         */
        private Date getDate() {
                Calendar ca = Calendar.getInstance();
                ca.set(Calendar.MINUTE, 0);
                ca.set(Calendar.SECOND, 0);
                return ca.getTime();
        }

        public List<SeckillTime> getTime() {
                // 获取当前时间及往后7个时间段, 总共8个
                Date time = getDate();
                return seckillTimeMapper.getTime(time.getTime() / 1000 * 1000);
        }

        public SeckillProductVo getSeckill(String seckillId) {
                // 从缓存查询
                Map<String, Object> map = redisTemplate.opsForHash().entries(RedisKey.SECKILL_PRODUCT + seckillId);

                if (!map.isEmpty()) {
                        SeckillProductVo so = null;
                        try {
                                so = BeanUtil.map2bean(map, SeckillProductVo.class);
                        } catch (Exception e) {
                                e.printStackTrace();
                        }finally {
                                return so;
                        }
                }

                // 数据库查询
                SeckillProductVo vo = seckillProductMapper.getSeckill(seckillId);
                if (vo != null) {
                        try {
                                // 存入缓存
                                redisTemplate.opsForHash().putAll(RedisKey.SECKILL_PRODUCT + seckillId, BeanUtil.bean2map(vo));
                                long ttl = vo.getEndTime() - System.currentTimeMillis();
                                redisTemplate.expire(RedisKey.SECKILL_PRODUCT + seckillId, ttl, TimeUnit.MILLISECONDS);
                                // 将库存单独存入一个key中
                                if (stringRedisTemplate.opsForValue().get(RedisKey.SECKILL_PRODUCT_STOCK + seckillId) == null) {
                                        stringRedisTemplate.opsForValue().set(RedisKey.SECKILL_PRODUCT_STOCK + seckillId, vo.getSeckillStock() + "", vo.getEndTime() - new Date().getTime(), TimeUnit.MILLISECONDS);
                                }
                        } catch (Exception e) {
                                e.printStackTrace();
                        }
                        return vo;
                }
                return null;
        }

        /**
         * 秒杀（P2 高并发闭环）：
         * SADD 防重复抢购 → 开始时间校验 → Lua 原子预扣库存 → 本地消息表(SENT) → 发 MQ 削峰
         * 消费侧（shop-order SeckillOrderListener）幂等落库；发布确认失败由 ConfirmCallback 置 FAILED 并回滚 Redis 库存。
         *
         * @param seckillId
         */
        public void seckillProduct(String seckillId, Integer userId) {
                // 1) SADD 原子防重：同一用户同一活动只允许抢一次（原来的防重在消费端才做，太迟）
                String userSetKey = RedisKey.SECKILL_PRODUCT_USER_SET + seckillId;
                Long added = stringRedisTemplate.opsForSet().add(userSetKey, userId.toString());
                if (added == null || added == 0) {
                        throw new XmException(ExceptionEnum.GET_SECKILL_IS_REUSE);
                }

                // 2) 判断秒杀是否开始, 防止路径暴露被刷
                Map m = redisTemplate.opsForHash().entries(RedisKey.SECKILL_PRODUCT + seckillId);
                SeckillProductVo seckillProductVo = null;
                if (!m.isEmpty()) {
                        try {
                                seckillProductVo = BeanUtil.map2bean(m, SeckillProductVo.class);
                        } catch (Exception e) {
                                e.printStackTrace();
                        }
                        if (seckillProductVo != null && seckillProductVo.getStartTime() > new Date().getTime()) {
                                // 尚未开始：回滚防重集合，放行下次再试
                                stringRedisTemplate.opsForSet().remove(userSetKey, userId.toString());
                                throw new XmException(ExceptionEnum.GET_SECKILL_IS_NOT_START);
                        }
                }

                // 3) Lua 脚本原子性检查库存并扣减
                /* if (localOverMap.get(seckillId) != null && localOverMap.get(seckillId)) {
                    // 售空
                    throw new XmException(ExceptionEnum.GET_SECKILL_IS_OVER);
                } */
                String luaScript = "if (redis.call('get', KEYS[1]) == false or tonumber(redis.call('get', KEYS[1])) <= 0) then return -1 else return redis.call('decr', KEYS[1]) end";

                Long result = stringRedisTemplate.execute(
                        (RedisCallback<Long>) connection -> connection.eval(
                                luaScript.getBytes(), ReturnType.INTEGER, 1,
                                (RedisKey.SECKILL_PRODUCT_STOCK + seckillId).getBytes()
                        )
                );

                if (result == null || result == -1) {
                        // 已售罄：回滚防重集合，保持"未抢到"可退
                        stringRedisTemplate.opsForSet().remove(userSetKey, userId.toString());
                        throw new XmException(ExceptionEnum.GET_SECKILL_IS_OVER);
                }

                // 4) 本地消息表落 SENT（先记录后投递；唯一索引兜底防重复插入）
                String correlationId = seckillId + ":" + userId;
                try {
                        SeckillMessageRecord record = new SeckillMessageRecord();
                        record.setMessageId(correlationId);
                        record.setSeckillId(seckillId);
                        record.setUserId(userId.toString());
                        record.setStatus("SENT");
                        record.setRetryCount(0);
                        messageRecordMapper.insert(record);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                        // 理论上被 SADD 挡住；兜底场景按重复抢购处理并回滚库存
                        stringRedisTemplate.opsForValue().increment(RedisKey.SECKILL_PRODUCT_STOCK + seckillId);
                        throw new XmException(ExceptionEnum.GET_SECKILL_IS_REUSE);
                }

                // 5) MQ 异步削峰，order 服务消费落库
                mqSend(seckillId, userId, correlationId);
        }

        /**
         * 秒杀结果查询（前端抢购后轮询）
         * @return status：未参与(null) / SENT(排队中) / CONSUMED(成功) / FAILED(失败)
         */
        public String getSeckillResult(String seckillId, Integer userId) {
                SeckillMessageRecord record = messageRecordMapper.findByMessageId(seckillId + ":" + userId);
                return record == null ? null : record.getStatus();
        }

        private void mqSend(String seckillId, Integer userId, String correlationId) {
                Map<String, String> msg = new HashMap<>();
                msg.put("seckillId", seckillId);
                msg.put("userId", userId.toString());
                msg.put("messageId", correlationId);

                // 消息持久化配置
                rabbitTemplate.convertAndSend(
                        "seckill_order",
                        msg,
                        message -> {
                                message.getMessageProperties().setCorrelationId(correlationId);
                                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                                return message;
                        },
                        new CorrelationData(correlationId)
                );
        }

        public Long getEndTime(String seckillId) {
                SeckillProductVo vo = seckillProductMapper.getSeckill(seckillId);
                return seckillTimeMapper.getEndTime(vo.getTimeId());
        }
}
