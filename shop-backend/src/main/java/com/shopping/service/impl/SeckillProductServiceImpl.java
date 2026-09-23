package com.shopping.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.SeckillProductMapper;
import com.shopping.mapper.SeckillTimeMapper;
import com.shopping.pojo.SeckillProduct;
import com.shopping.pojo.SeckillTime;
import com.shopping.util.BeanUtil;
import com.shopping.util.RedisKey;
import com.shopping.vo.SeckillProductVo;
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

        public void addSeckillProduct(SeckillProduct seckillProduct) {
                // TODO: 仿添加秒杀商品
                Date time = getDate();
                long startTime = time.getTime() / 1000 * 1000 + 1000 * 60 * 60;
                long endTime = startTime + 1000 * 60 * 60;

                // 构造时间段查询条件
                SeckillTime one = seckillTimeMapper.selectOne(new LambdaQueryWrapper<SeckillTime>()
                        .eq(SeckillTime::getStartTime, startTime)
                        .eq(SeckillTime::getEndTime, endTime)
                );

                if (one == null) {
                        SeckillTime  seckillTime = new SeckillTime(null, startTime, endTime);
                        seckillTimeMapper.insert(seckillTime);
                }else seckillProduct.setTimeId(one.getTimeId());
                seckillProductMapper.insert(seckillProduct);
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
         * 秒杀
         *
         * @param seckillId
         */
        @Transactional
        public void seckillProduct(String seckillId, Integer userId) {
                // 使用 Lua 脚本原子性检查库存并扣减
                /* if (localOverMap.get(seckillId) != null && localOverMap.get(seckillId)) {
                    // 售空
                    throw new XmException(ExceptionEnum.GET_SECKILL_IS_OVER);
                } */
                // 判断秒杀是否开始, 防止路径暴露被刷
                Map m = redisTemplate.opsForHash().entries(RedisKey.SECKILL_PRODUCT + seckillId);
                if (!m.isEmpty()) {
                        SeckillProductVo seckillProductVo = null;
                        try {
                                seckillProductVo = BeanUtil.map2bean(m, SeckillProductVo.class);
                        } catch (Exception e) {
                                e.printStackTrace();
                        }
                        // 秒杀开始时间
                        Long startTime = seckillProductVo.getStartTime();

                        if (startTime > new Date().getTime()) {
                                throw new XmException(ExceptionEnum.GET_SECKILL_IS_NOT_START);
                        }
                }
                // Lua 脚本代码
                String luaScript = "if (redis.call('get', KEYS[1]) == false or tonumber(redis.call('get', KEYS[1])) <= 0) then return -1 else return redis.call('decr', KEYS[1]) end";


                //  执行Lua脚本
                Long result = stringRedisTemplate.execute(
                        (RedisCallback<Long>) connection -> connection.eval(
                                luaScript.getBytes(), ReturnType.INTEGER, 1,
                                (RedisKey.SECKILL_PRODUCT_STOCK + seckillId).getBytes()
                        )
                );

                if (result == -1) {
                        throw new XmException(ExceptionEnum.GET_SECKILL_IS_OVER);
                }

                // 使用RabbitMQ异步传输
                mqSend(seckillId, userId);
        }

        private void mqSend(String seckillId, Integer userId) {
                Map<String, String> msg = new HashMap<>();
                msg.put("seckillId", seckillId);
                msg.put("userId", userId.toString());
                String correlationId = seckillId + ":" + userId;

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
