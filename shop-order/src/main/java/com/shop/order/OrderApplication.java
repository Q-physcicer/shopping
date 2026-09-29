package com.shop.order;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * shop-order 启动类
 */
@SpringBootApplication(scanBasePackages = {"com.shop.order", "com.shop.common"})
@EnableFeignClients(basePackages = "com.shop.feign")
@MapperScan("com.shop.order.mapper")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
