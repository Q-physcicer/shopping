package com.shop.product.config;

import com.shop.common.util.RedisKey;
import com.shop.product.mapper.SeckillMessageRecordMapper;
import com.shop.product.pojo.SeckillMessageRecord;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * product 端 RabbitMQ 配置（P2）：
 * - 队列/交换机声明与 order 端一致（双方声明幂等，谁先启动都能建好）
 * - publisher confirm：NACK/回退 -> 消息表置 FAILED + Redis 库存回滚
 */
@Configuration
public class RabbitMQConfig implements RabbitTemplate.ConfirmCallback {

    @Autowired
    private SeckillMessageRecordMapper messageRecordMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Bean
    public Queue queue() {
        return QueueBuilder.durable("seckill_order")
                .withArgument("x-dead-letter-exchange", "seckill_dead_letter_exchange")
                .withArgument("x-dead-letter-routing-key", "seckill_dead_letter_key")
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("seckill_dead_letter_queue").build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("seckill_dead_letter_exchange");
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("seckill_dead_letter_key");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        rabbitTemplate.setConfirmCallback(this);   // 需要 spring.rabbitmq.publisher-confirm-type: correlated
        return rabbitTemplate;
    }

    /**
     * 发布确认回调：仅处理 NACK（发送失败兜底），置 FAILED 并回滚 Redis 预扣库存。
     * 抢购消息的 CorrelationData id = seckillId:userId。
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack || correlationData == null) {
            return;
        }
        String[] parts = correlationData.getId().split(":");
        if (parts.length != 2) {
            return;
        }
        String seckillId = parts[0];
        String userId = parts[1];
        messageRecordMapper.markFailedIfSent(correlationData.getId(), "broker nack: " + cause);
        rollbackRedisStock(seckillId);
    }

    private void rollbackRedisStock(String seckillId) {
        try {
            StringRedisTemplate t = stringRedisTemplate;
            String key = RedisKey.SECKILL_PRODUCT_STOCK + seckillId;
            Long stock = t.opsForValue().increment(key);
            if (stock != null && stock == 1L) {
                // 回滚到 1 说明此前已售罄（0），补一条 10s 过期防止缓存雪崩式穿透（与原缓存语义一致）
                t.expire(key, 10, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // Redis 异常时由 message_record FAILED 状态兜底，管理端可查
        }
    }
}