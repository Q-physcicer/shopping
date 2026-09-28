package com.shop.order.mq;

import com.rabbitmq.client.Channel;
import com.shop.common.util.Result;
import com.shop.order.dto.SeckillMessage;
import com.shop.order.mapper.SeckillMessageRecordMapper;
import com.shop.order.pojo.SeckillMessageRecord;
import com.shop.order.service.impl.OrderServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀下单消费者（P2 闭环核心）：
 * 幂等策略——本地消息表 CAS(SENT→CONSUMED) 抢占处理权，抢占成功才落库；
 * 落库失败回退 CONSUMED→SENT 并抛异常，交给 RetryInterceptor 3 次退避重试，
 * 重试尽头 reject 进入 seckill_dead_letter_queue（SeckillDeadLetterListener 兜底回滚）。
 */
@Component
public class SeckillOrderListener {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderListener.class);

    @Autowired
    private SeckillMessageRecordMapper messageRecordMapper;
    @Autowired
    private OrderServiceImpl orderService;

    @RabbitListener(queues = "seckill_order", containerFactory = "rabbitListenerContainerFactory")
    public void onMessage(@Payload SeckillMessage msg, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String messageId = msg.getMessageId();
        if (messageId == null) {
            // 兼容老消息格式（无 messageId）
            messageId = msg.getSeckillId() + ":" + msg.getUserId();
        }
        try {
            boolean consumed = consume(msg, messageId);
            channel.basicAck(tag, false);
            if (consumed) {
                log.info("[SeckillOrderListener] 秒杀订单落库完成 messageId={}", messageId);
            }
        } catch (Exception e) {
            // 回退状态并让 retry interceptor 处理（basicNack requeue 或本地重试）
            try {
                messageRecordMapper.markRetry(messageId, "consume error: " + e.getMessage());
            } catch (Exception ignored) {
            }
            throw new RuntimeException("seckill consume failed: " + e.getMessage(), e);   // 抛出让拦截器重试
        }
    }

    /**
     * 幂等消费。
     * @return true=本次实际落库；false=重复消息/已被消费（直接 ack 跳过）
     */
    private boolean consume(SeckillMessage msg, String messageId) {
        SeckillMessageRecord record = messageRecordMapper.findByMessageId(messageId);
        if (record == null) {
            // 无记录的消息（异常场景）：不落库，避免无消息表保护的盲写
            log.warn("[SeckillOrderListener] 消息表无记录，丢弃 messageId={}", messageId);
            return false;
        }
        if ("CONSUMED".equals(record.getStatus())) {
            return false;   // 已消费，幂等跳过
        }
        // CAS 抢占处理权：只有 SENT → CONSUMED 成功的实例执行落库；
        // 竞争失败（他人已处理）直接放弃重试
        int grabbed = messageRecordMapper.casUpdateStatus(messageId, "SENT", "CONSUMED");
        if (grabbed == 0) {
            return false;
        }
        try {
            orderService.addSeckillOrder(msg.getSeckillId(), msg.getUserId());
            return true;
        } catch (Exception e) {
            // 落库失败：回退 CONSUMED→SENT，让本消息重新可被消费（重试链）
            messageRecordMapper.casUpdateStatus(messageId, "CONSUMED", "SENT");
            throw e;
        }
    }
}