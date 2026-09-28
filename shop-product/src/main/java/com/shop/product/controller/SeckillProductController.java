package com.shop.product.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import com.shop.product.pojo.SeckillProduct;
import com.shop.product.pojo.SeckillTime;
import com.shop.product.service.impl.SeckillProductServiceImpl;
import com.shop.product.vo.SeckillProductVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀商品控制类。
 * P1：抢购人身份从网关注入的 X-User-Id 取。
 * P2：Sentinel 限流入口 + 抢购结果轮询接口。
 *
 * @Auther: wdd
 */
@RestController
@RequestMapping("/seckill/product")
public class SeckillProductController {

    @Autowired
    private SeckillProductServiceImpl spsi;

    /** 根据时间id获取对应时间的秒杀商品列表 */
    @GetMapping("/time/{timeId}")
    public Result getProduct(@PathVariable String timeId) {
        List<SeckillProductVo> seckillProductVos = spsi.getProduct(timeId);
        return Result.success("success", seckillProductVos);
    }

    /** 获取秒杀商品 */
    @GetMapping("/{seckillId}")
    public Result getSeckill(@PathVariable String seckillId) {
        SeckillProductVo seckillProductVo = spsi.getSeckill(seckillId);
        return Result.success("success", seckillProductVo);
    }

    /** 获取时间段 */
    @GetMapping("/time")
    public Result getTime() {
        List<SeckillTime> seckillTimes = spsi.getTime();
        return Result.success("success", seckillTimes);
    }

    /**
     * 开始秒杀（Sentinel 限流：接口 QPS 1000/机 + seckillId 热点维度 200 QPS）
     * 网关已鉴权，身份必然存在；代码兜底防内部直连误用
     */
    @PostMapping("/seckill/{seckillId}")
    @SentinelResource(value = SentinelResourceNames.SECKILL, blockHandler = "seckillBlocked")
    public Result seckillProduct(@PathVariable String seckillId) {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        spsi.seckillProduct(seckillId, uid.intValue());
        return Result.success("001", "排队中");
    }

    /** Sentinel 限流兜底：快速失败，避免把洪峰压进 MQ */
    public Result seckillBlocked(String seckillId, BlockException ex) {
        return Result.fail("当前排队人数过多，请稍后再试", null);
    }

    /**
     * 秒杀结果查询（P2 新增；抢购返回"排队中"后前端轮询本接口）
     * @return data: null=未参与 / SENT=排队中 / CONSUMED=成功 / FAILED=失败
     */
    @GetMapping("/result/{seckillId}")
    public Result seckillResult(@PathVariable String seckillId) {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return Result.success("success", spsi.getSeckillResult(seckillId, uid.intValue()));
    }
}