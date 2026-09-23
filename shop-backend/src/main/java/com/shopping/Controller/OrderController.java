package com.shopping.Controller;

import com.shopping.service.impl.OrderServiceImpl;
import com.shopping.util.Result;
import com.shopping.vo.CartVo;
import com.shopping.vo.OrderVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Description: 订单模块
 */
@RestController
@RequestMapping("/order")
public class OrderController {
    
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private OrderServiceImpl osi;

    @PostMapping("") // 添加订单
    public Result addOrder(@RequestBody List<CartVo> cartVoList, @CookieValue("XM_TOKEN") String cookie) {
        // 先判断cookie是否存在，和redis校验
        Integer userId = (Integer) redisTemplate.opsForHash().get(cookie, "userId");
        osi.addOrder(cartVoList, userId);
        return Result.success("success", "下单成功");
    }

    @GetMapping("") //  获取订单
    public Result getOrder(@CookieValue("XM_TOKEN") String cookie) {
        // 先判断cookie是否存在，和redis校验
        Integer userId = (Integer) redisTemplate.opsForHash().get(cookie, "userId");
        List<List<OrderVo>> orders = osi.getOrder(userId);
        return Result.success("success", orders);
    }

}
