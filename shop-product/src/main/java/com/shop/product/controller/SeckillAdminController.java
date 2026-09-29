package com.shop.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.util.RedisKey;
import com.shop.common.util.Result;
import com.shop.product.mapper.SeckillProductMapper;
import com.shop.product.mapper.SeckillTimeMapper;
import com.shop.product.pojo.Product;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.pojo.SeckillProduct;
import com.shop.product.pojo.SeckillTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * 秒杀管理（P1 正轨化）：admin 经 Feign 调用（FeignIdentityInterceptor 透传 ADMIN 身份）。
 * P0 前缺陷：admin 服务直插 product 域表且不清 product 侧 Redis 缓存，
 * 新配置的商品在缓存 TTL 内对购物端不可见。
 */
@RestController
@RequestMapping("/seckill/admin")
public class SeckillAdminController {

    @Autowired
    private SeckillTimeMapper seckillTimeMapper;
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 新增秒杀商品（manual 场次；当前整点 +offsetHours 开场，场时长 1 小时）。
     * body: productId, seckillPrice, seckillStock, offsetHours(可选,默认 1)
     */
    @PostMapping("/add")
    public Result add(@RequestBody Map<String, Object> body) {
        // ADMIN 硬校验（网关 /seckill/** 无 ADMIN 路由位，此处是唯一闸）
        com.shop.common.context.UserContext.Principal p = com.shop.common.context.UserContext.get();
        if (p == null || !p.isAdmin()) {
            return Result.fail("无权配置秒杀（需管理员身份）", null);
        }
        Integer productId;
        Double price;
        Integer stock;
        int offsetHours;
        try {
            productId = Integer.valueOf(String.valueOf(body.get("productId")));
            price = Double.valueOf(String.valueOf(body.get("seckillPrice")));
            stock = Integer.valueOf(String.valueOf(body.get("seckillStock")));
            offsetHours = body.get("offsetHours") == null ? 1
                    : Integer.parseInt(String.valueOf(body.get("offsetHours")));
        } catch (NumberFormatException e) {
            return Result.fail("参数格式错误（价格/库存/小时须为数字）", null);
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.fail("商品不存在", null);
        }
        if (price == null || price <= 0 || price > product.getProductSellingPrice()) {
            return Result.fail("秒杀价必须大于 0 且不高于商品售价", null);
        }
        if (stock == null || stock < 1 || stock > product.getProductNum()) {
            return Result.fail("秒杀库存必须 ≥1 且不高于商品库存", null);
        }
        if (offsetHours < 0 || offsetHours > 72) {
            return Result.fail("开场时间偏移须在 0-72 小时之间", null);
        }

        Calendar ca = Calendar.getInstance();
        ca.set(Calendar.MINUTE, 0);
        ca.set(Calendar.SECOND, 0);
        long startTime = ca.getTimeInMillis() / 1000 * 1000 + offsetHours * 3600_000L;

        SeckillTime one = seckillTimeMapper.selectOne(new LambdaQueryWrapper<SeckillTime>()
                .eq(SeckillTime::getStartTime, startTime)
                .eq(SeckillTime::getEndTime, startTime + 3600_000L));
        if (one == null) {
            one = new SeckillTime();
            one.setStartTime(startTime);
            one.setEndTime(startTime + 3600_000L);
            one.setSource("manual");
            seckillTimeMapper.insert(one);
        } else if (!"manual".equals(one.getSource())) {
            // auto 场已有同窗口：不允许往里塞手动商品（避免定时重建后语义混乱）
            return Result.fail("该时段已有定时场次，请换一个时间", null);
        }

        // 同场同商品幂等：已存在则更新价格/库存
        SeckillProduct exist = seckillProductMapper.selectOne(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getTimeId, one.getTimeId())
                .eq(SeckillProduct::getProductId, productId));
        if (exist != null) {
            exist.setSeckillPrice(price);
            exist.setSeckillStock(stock);
            seckillProductMapper.updateById(exist);
        } else {
            SeckillProduct sp = new SeckillProduct();
            sp.setProductId(productId);
            sp.setSeckillPrice(price);
            sp.setSeckillStock(stock);
            sp.setTimeId(one.getTimeId());
            seckillProductMapper.insert(sp);
        }

        // 关键修复：失效该场次列表缓存（原 admin 直插库不清缓存，TTL 内购物端看不到新商品）
        stringRedisTemplate.delete(RedisKey.SECKILL_PRODUCT_LIST + one.getTimeId());

        return Result.success("秒杀商品已添加（" + offsetHours + " 小时后开始，场次为手动场不会被每日重建清理）");
    }
}
