package com.shop.order.mq;

import com.rabbitmq.client.Channel;
import com.shop.common.util.RedisKey;
import com.shop.order.dto.SeckillMessage;
import com.shop.order.pojo.SeckillMessageRecord;
import com.shop.order.mapper.SeckillMessageRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀死信消费者：消息重试 3 次仍失败后到达。
 * 兜底动作：消息表置 FAILED → Redis 预扣库存回滚 → 清防重集合与消息记录（允许用户重新抢购）。
 */
@Component
public class SeckillDeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(SeckillDeadLetterListener.class);

    @Autowired
    private SeckillMessageRecordMapper messageRecordMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = "seckill_dead_letter_queue", containerFactory = "rabbitListenerContainerFactory")
    public void onDeadLetter(@Payload SeckillMessage msg, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String messageId = msg.getMessageId() != null ? msg.getMessageId()
                : msg.getSeckillId() + ":" + msg.getUserId();
        try {
            // 1) 消息表最终态 FAILED
            messageRecordMapper.casUpdateStatus(messageId, "SENT", "FAILED");
            messageRecordMapper.casUpdateStatus(messageId, "CONSUMED", "FAILED");
            // 2) Redis 预扣库存回滚
            rollbackRedisStock(msg.getSeckillId());
            // 3) 清防重集合 + 删除消息记录，允许用户重新抢购（否则 SADD/唯一索引都会拦截）
            stringRedisTemplate.opsForSet().remove(
                    RedisKey.SECKILL_PRODUCT_USER_SET + msg.getSeckillId(), msg.getUserId());
            messageRecordMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SeckillMessageRecord>()
                            .eq(SeckillMessageRecord::getMessageId, messageId));
            log.warn("[SeckillDeadLetter] 秒杀消息最终失败已回滚 messageId={}", messageId);
        } catch (Exception e) {
            log.error("[SeckillDeadLetter] 死信处理异常 messageId={}", messageId, e);
        } finally {
            channel.basicAck(tag, false);   // 死信是终点站，无论如何 ack 防止无限循环
        }
    }

    private void rollbackRedisStock(String seckillId) {
        String key = RedisKey.SECKILL_PRODUCT_STOCK + seckillId;
        Long stock = stringRedisTemplate.opsForValue().increment(key);
        if (stock != null && stock == 1L) {
            stringRedisTemplate.expire(key, 10, TimeUnit.SECONDS);
        }
    }
}