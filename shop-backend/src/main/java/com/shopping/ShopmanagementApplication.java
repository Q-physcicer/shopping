package com.shopping;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.shopping.mapper")
public class ShopmanagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopmanagementApplication.class, args);
    }

}
