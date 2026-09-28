package com.shop.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * shop-chat 启动类
 */
@SpringBootApplication(scanBasePackages = {"com.shop.chat", "com.shop.common"})
@EnableFeignClients(basePackages = "com.shop.feign")
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
