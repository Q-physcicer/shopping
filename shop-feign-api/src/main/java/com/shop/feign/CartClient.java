package com.shop.feign;

import com.shop.common.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * cart 服务客户端（chat Agent 代客加购/查车）。
 */
@FeignClient(name = "shop-cart", contextId = "cartClient")
public interface CartClient {

    /** 加入购物车（每次 +1）；老接口返回 ResultMessage 结构 {code, msg, data}，用 Map 接 */
    @PostMapping("/cart/product/{productId}")
    Map<String, Object> addCart(@PathVariable("productId") Integer productId);

    /** 查询当前用户购物车 */
    @GetMapping("/cart/user")
    Result getMyCart();
}