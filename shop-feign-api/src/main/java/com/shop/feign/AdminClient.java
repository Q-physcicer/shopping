package com.shop.feign;

import com.shop.common.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * admin 服务客户端（P5：chat 管理 Agent 工具消费）。
 * 注意：admin Controller 依赖网关做 ADMIN 校验；chat 直连 Feign 不经网关，
 * 因此管理工具（AdminTools）内部必须校验 toolContext 的 role=ADMIN。
 */
@FeignClient(name = "shop-admin", contextId = "adminClient")
public interface AdminClient {

    /** 上架新商品 */
    @PostMapping("/admin/product")
    Result publishProduct(@RequestBody Map<String, Object> product);

    /** 改价/改库存 */
    @PostMapping("/admin/product/{productId}/update")
    Result updateProduct(@PathVariable("productId") Integer productId,
                         @RequestBody Map<String, Object> body);

    /** 统计看板数据聚合 */
    @GetMapping("/admin/stats/overview")
    Result overview(@RequestParam(value = "days", defaultValue = "7") int days);
}