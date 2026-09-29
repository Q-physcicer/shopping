package com.shop.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.util.Result;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.pojo.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * product 内部统计接口（P5，admin 统计聚合经 Feign 调用；业务端口不对公网开放）。
 * 网关已对 /product/internal/** 403；此为第二道闸（业务端口暴露时的纵深防御）。
 */
@RestController
@RequestMapping("/product/internal")
public class ProductInternalController {

    @Autowired
    private ProductMapper productMapper;

    /** 统一 ADMIN 硬校验 */
    private Result requireAdmin() {
        com.shop.common.context.UserContext.Principal p = com.shop.common.context.UserContext.get();
        if (p == null || !p.isAdmin()) {
            return Result.fail("无权访问内部接口（需管理员身份）", null);
        }
        return null;
    }

    /** 销量 Top N */
    @GetMapping("/stats/top-sales")
    public Result topSales(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        List<Product> top = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getProductSales)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
        return Result.success("success", top);
    }

    /** 低库存（低于阈值的商品，供运营告警） */
    @GetMapping("/stats/low-stock")
    public Result lowStock(@RequestParam(value = "threshold", defaultValue = "5") int threshold) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        List<Product> low = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .lt(Product::getProductNum, Math.min(Math.max(threshold, 0), 1000)));
        return Result.success("success", low);
    }
}