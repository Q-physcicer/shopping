package com.shop.feign;

import com.shop.common.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * product 服务客户端（chat Agent / admin 统计等消费）。
 * Result 由调用方解包；身份 header 由 FeignIdentityInterceptor 自动透传。
 */
@FeignClient(name = "shop-product", contextId = "productClient")
public interface ProductClient {

    /** 搜索（ES/MySQL 双引擎） */
    @GetMapping("/product/search")
    Result search(@RequestParam("keyword") String keyword,
                  @RequestParam(value = "categoryId", required = false) Integer categoryId,
                  @RequestParam(value = "sort", defaultValue = "relevance") String sort,
                  @RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "size", defaultValue = "5") int size);

    /** 商品详情 */
    @GetMapping("/product/{productId}")
    Result getProductDetail(@PathVariable("productId") Integer productId);

    /** 分类列表 */
    @GetMapping("/category")
    Result listCategories();

    /** 销量 Top N（admin 统计） */
    @GetMapping("/product/internal/stats/top-sales")
    Result topSales(@RequestParam(value = "limit", defaultValue = "10") int limit);

    /** ES 全量重建（admin 系统设置） */
    @PostMapping("/product/internal/es/rebuild")
    Result rebuildEs();
}