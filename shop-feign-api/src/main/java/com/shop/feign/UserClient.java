package com.shop.feign;

import com.shop.common.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * user 服务客户端（P5 admin 统计聚合消费）。
 */
@FeignClient(name = "shop-user", contextId = "userClient")
public interface UserClient {

    /** 用户总数（admin 统计用）；P5 在 user 服务补充 /user/internal/stats/count */
    @GetMapping("/user/internal/stats/count")
    Result userCount();

    /** 按用户名查询用户 */
    @GetMapping("/user/internal/byname")
    Result getByUsername(@RequestParam("username") String username);

    /** 近 N 日注册增长曲线（admin） */
    @GetMapping("/user/internal/stats/growth")
    Result growth(@RequestParam(value = "days", defaultValue = "7") int days);
}