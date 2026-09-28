package com.shop.cart;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * shop-cart 启动类
 */
@SpringBootApplication(scanBasePackages = {"com.shop.cart", "com.shop.common"})
@MapperScan("com.shop.cart.mapper")
public class CartApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
