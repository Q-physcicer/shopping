package com.shop.order.mq;

import com.rabbitmq.client.Channel;
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

/**
 * 订单超时取消消费者（TTL 30min → 死信 → 本监听器）：
 * CAS(待支付→已取消) 抢占取消权（与支付回调竞争时只赢一个），成功则回滚库存（秒杀库存/普通商品库存）。
 */
@Component
public class OrderTimeoutListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutListener.class);

    @Autowired
    private OrderServiceImpl orderService;

    @RabbitListener(queues = "order.pay.cancel.queue", containerFactory = "rabbitListenerContainerFactory")
    public void onTimeout(@Payload String orderId, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            boolean cancelled = orderService.cancelOrderIfTimeout(orderId);
            if (cancelled) {
                log.info("[OrderTimeout] 订单超时未支付已取消 orderId={}", orderId);
            }
            // 未取消（已支付等）直接吞掉，ack 结束
        } catch (Exception e) {
            log.error("[OrderTimeout] 超时取消异常 orderId={}", orderId, e);
            // 取消失败不阻塞支付主链路；有-error log 可查，也可以演进为告警
        } finally {
            channel.basicAck(tag, false);
        }
    }
}