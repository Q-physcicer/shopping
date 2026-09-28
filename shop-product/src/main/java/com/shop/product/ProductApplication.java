package com.shop.product;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * shop-product 启动类
 */
@SpringBootApplication(scanBasePackages = {"com.shop.product", "com.shop.common"})
@MapperScan("com.shop.product.mapper")
@EnableScheduling   // P2：SeckillTask 定时生成秒杀活动
public class ProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
