package com.shopping;

import com.shopping.service.impl.OrderServiceImpl;
import com.shopping.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 上下文加载测试：验证 Spring 容器能完整启动，
 * 覆盖 数据源/MyBatis 绑定/Redis/RabbitMQ/Spring AI(ChatClient) 等核心装配。
 * 不依赖具体的业务数据，可通过 mvn package 稳定跑过。
 */
@SpringBootTest
class ShopmanagementApplicationTests {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private OrderServiceImpl orderService;

    @Autowired
    private ChatClient chatClient;

    @Test
    void contextLoads() {
        assertNotNull(userService, "UserService 应完成装配");
        assertNotNull(orderService, "OrderService 应完成装配");
        assertNotNull(chatClient, "Spring AI ChatClient 应完成装配");
    }
}