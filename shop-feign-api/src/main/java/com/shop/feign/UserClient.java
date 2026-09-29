package com.shop.feign;

import com.shop.common.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * user 服务客户端（P5 admin 统计聚合消费；P0-6 下单地址回查）。
 */
@FeignClient(name = "shop-user", contextId = "userClient")
public interface UserClient {

    /** 用户总数（admin 统计用）；P5 在 user 服务补充 /user/internal/stats/count */
    @GetMapping("/user/internal/stats/count")
    Result userCount();

    /** 近 N 日注册增长曲线（admin） */
    @GetMapping("/user/internal/stats/growth")
    Result growth(@RequestParam(value = "days", defaultValue = "7") int days);

    /** 按 ID 查当前登录用户的地址（P0-6 唯一调用场景：order 下单回查地址做快照） */
    @GetMapping("/user/address/internal/{id}")
    Result getAddress(@PathVariable("id") Long id);

    /** 当前登录用户的默认地址（P0-6：Agent 直购无 addressId 时兜底） */
    @GetMapping("/user/address/internal/default")
    Result getDefaultAddress();
}