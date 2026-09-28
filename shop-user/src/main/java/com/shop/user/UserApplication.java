package com.shop.user;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * shop-user 启动类
 */
@SpringBootApplication(scanBasePackages = {"com.shop.user", "com.shop.common"})
@MapperScan("com.shop.user.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
