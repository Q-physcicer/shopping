package com.shop.admin;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * shop-admin 启动类
 */
@SpringBootApplication(scanBasePackages = {"com.shop.admin", "com.shop.common"})
@MapperScan("com.shop.admin.mapper")
@EnableFeignClients(basePackages = "com.shop.feign")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
