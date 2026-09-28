package com.shop.product.mq;

import com.shop.product.es.ProductEsService;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.pojo.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import java.io.IOException;

/**
 * 商品变更 ES 同步（P5）：
 * admin 服务上架/改价/改库存/下架后发 topic `shop.product.change`（routing key: product.update / product.delete），
 * 本消费者从 DB 读最新商品同步到 product_index。
 * 网络抖动重投可能造成重复 syncOne——save 幂等，无害。
 */
@Slf4j
@Component
public class ProductChangeConsumer {

    public static final String EXCHANGE = "shop.product.change";
    public static final String QUEUE = "shop.product.es.sync";
    public static final String KEY_UPDATE = "product.update";
    public static final String KEY_DELETE = "product.delete";

    @Autowired
    private ProductEsService productEsService;
    @Autowired
    private ProductMapper productMapper;

    @RabbitListener(queues = QUEUE)
    public void onChange(@Payload String productIdStr, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            Integer productId = Integer.valueOf(productIdStr.trim());
            productEsService.syncOne(productMapper.selectById(productId));
            log.info("[ES-Sync] 商品变更已同步索引 productId={}", productId);
        } catch (Exception e) {
            log.error("[ES-Sync] 同步失败 productId={}", productIdStr, e);
        } finally {
            channel.basicAck(tag, false);
        }
    }

    @Configuration
    static class Topology {
        @Bean
        public TopicExchange productChangeExchange() {
            return new TopicExchange(EXCHANGE);
        }

        @Bean
        public Queue productSyncQueue() {
            return QueueBuilder.durable(QUEUE).build();
        }

        @Bean
        public Binding productSyncBinding(TopicExchange productChangeExchange) {
            return BindingBuilder.bind(productSyncQueue())
                    .to(productChangeExchange).with("product.#");
        }
    }
}