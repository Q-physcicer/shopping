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
 */
@RestController
@RequestMapping("/product/internal")
public class ProductInternalController {

    @Autowired
    private ProductMapper productMapper;

    /** 销量 Top N */
    @GetMapping("/stats/top-sales")
    public Result topSales(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<Product> top = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getProductSales)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
        return Result.success("success", top);
    }

    /** 低库存（低于阈值的商品，供运营告警） */
    @GetMapping("/stats/low-stock")
    public Result lowStock(@RequestParam(value = "threshold", defaultValue = "5") int threshold) {
        List<Product> low = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .lt(Product::getProductNum, threshold));
        return Result.success("success", low);
    }
}