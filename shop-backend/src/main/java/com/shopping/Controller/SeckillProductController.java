package com.shopping.Controller;

import com.shopping.pojo.SeckillProduct;
import com.shopping.pojo.SeckillTime;
import com.shopping.service.impl.SeckillProductServiceImpl;
import com.shopping.util.Result;
import com.shopping.util.ResultMessage;
import com.shopping.vo.SeckillProductVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Auther: wdd
 * @Date: 2020-03-28 19:58
 * @Description:
 */
@RestController
@RequestMapping("/seckill/product")
public class SeckillProductController {

    @Autowired
    private SeckillProductServiceImpl spsi;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据时间id获取对应时间的秒杀商品列表
     * @param timeId
     * @return
     */
    @GetMapping("/time/{timeId}")
    public Result getProduct(@PathVariable String timeId) {
        List<SeckillProductVo> seckillProductVos = spsi.getProduct(timeId);
        return Result.success("success", seckillProductVos);
    }

    /**
     * 获取秒杀商品
     * @param seckillId
     * @return
     */
    @GetMapping("/{seckillId}")
    public Result getSeckill(@PathVariable String seckillId) {
        SeckillProductVo seckillProductVo = spsi.getSeckill(seckillId);
        return Result.success("success", seckillProductVo);
    }

    /**
     * 获取时间段
     * @return
     */
    @GetMapping("/time")
    public Result getTime() {
        List<SeckillTime> seckillTimes = spsi.getTime();
        return Result.success("success", seckillTimes);
    }

    /**
     * 添加秒杀商品
     * @param seckillProduct
     * @return
     */
    @PostMapping("")
    public Result addSeckillProduct(@RequestBody SeckillProduct seckillProduct) {
        spsi.addSeckillProduct(seckillProduct);
        return Result.success("添加成功");
    }

    /**
     * 开始秒杀
     * @param seckillId
     * @return
     */
    @PostMapping("/seckill/{seckillId}")
    public Result seckillProduct(@PathVariable String seckillId, @CookieValue("XM_TOKEN") String cookie) {
        // 先判断cookie是否存在，和redis校验
        Integer userId = (Integer) redisTemplate.opsForHash().get(cookie, "userId");
        spsi.seckillProduct(seckillId, userId);
        return Result.success("001", "排队中");
    }


}
