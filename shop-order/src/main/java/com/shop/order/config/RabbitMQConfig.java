package com.shop.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * order 端 RabbitMQ 配置（P2 消费端）：
 * - seckill_order：秒杀下单队列（product 端声明一致，本端声明幂等兜底，谁先启动都能建好拓扑）
 * - order.pay.timeout.queue：无消费者的 TTL 缓冲队列，消息到期死信 → order.cancel.exchange
 * - order.pay.cancel.queue：取消订单死信队列，OrderTimeoutListener 消费（CAS 取消 + 库存回滚）
 */
@Configuration
public class RabbitMQConfig {

    // ---------------- 秒杀削峰队列 ----------------

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

    // ---------------- 订单超时（TTL + 死信，原生方案不装延迟插件） ----------------

    @Bean
    public Queue orderPayTimeoutQueue() {
        return QueueBuilder.durable("order.pay.timeout.queue")
                .withArgument("x-dead-letter-exchange", "order.cancel.exchange")
                .withArgument("x-dead-letter-routing-key", "order.cancel.key")
                .build();
    }

    @Bean
    public Queue orderPayCancelQueue() {
        return QueueBuilder.durable("order.pay.cancel.queue").build();
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange("order.cancel.exchange");
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderPayCancelQueue())
                .to(orderCancelExchange())
                .with("order.cancel.key");
    }

    // ---------------- 通用 ----------------

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public RetryOperationsInterceptor retryOperationsInterceptor() {
        return RetryInterceptorBuilder.stateless()
                        .maxAttempts(3)                                // 最多 3 次重试
                        .backOffOptions(1000, 2.0, 10000)              // 1s 起、2 倍退避、最长 10s
                        .recoverer(new RejectAndDontRequeueRecoverer()) // 重试尽 → 死信
                        .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
                    ConnectionFactory connectionFactory, Jackson2JsonMessageConverter jsonMessageConverter,
                    RetryOperationsInterceptor retryOperationsInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryOperationsInterceptor);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);   // 削峰：单消费者在途最多 10 条
        return factory;
    }
}