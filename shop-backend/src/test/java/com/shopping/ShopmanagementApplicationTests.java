package com.shopping;

import com.shopping.Controller.OrderController;
import com.shopping.service.impl.OrderServiceImpl;
import com.shopping.util.Result;
import com.shopping.vo.OrderVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shopping.pojo.User;
import com.shopping.service.impl.UserServiceImpl;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

@SpringBootTest
class ShopmanagementApplicationTests {

    //private static final Logger logger = LoggerFactory.getLogger(ShopmanagementApplicationTests.class);

    @Autowired
    private UserServiceImpl u;
    @Autowired
    private OrderServiceImpl osi;
    @Autowired
    private RedisTemplate  redisTemplate;

    @Test
    void contextLoads() {
        String cookie = "1cdcfcc9c2718cea653b8414f589cd5c|33|qwert|";
        Integer userId = (Integer) redisTemplate.opsForHash().get(cookie, "userId");
        System.out.println(userId);
        List<List<OrderVo>> orders = osi.getOrder(userId);
        System.out.println(orders);
    }
}

