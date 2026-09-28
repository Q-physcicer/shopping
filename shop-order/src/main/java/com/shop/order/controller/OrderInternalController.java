package com.shop.order.controller;

import com.shop.common.context.UserContext;
import com.shop.common.util.Result;
import com.shop.order.mapper.OrderMapper;
import com.shop.order.service.impl.AftersaleServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * order 内部统计接口（P5，admin 统计聚合经 Feign 调用）。
 * GMV 口径：已支付(status=1,3)订单的 productPrice*productNum。
 *
 * ⚠️ /order/internal/** 经网关对外可达（路由 /order/**），读写接口不能只靠"没人知道"防护——
 * 售后审批等有资金/状态语义的写操作必须在 order 端硬校验 ADMIN
 * （Feign 经 FeignIdentityInterceptor / 网关经 AuthGlobalFilter 两条路径都会注入 X-User-Role）。
 */
@RestController
@RequestMapping("/order/internal")
public class OrderInternalController {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private AftersaleServiceImpl aftersaleService;

    /** GMV 概览：总 GMV / 总订单数 / 待支付数 / 已取消数 + 近 N 日每日曲线 */
    @GetMapping("/stats/gmv")
    public Result gmv(@RequestParam(value = "days", defaultValue = "7") int days) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalGmv", jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(product_price * product_num),0) FROM `order` WHERE status IN (1,3)", Double.class));
        data.put("totalOrders", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order`", Long.class));
        data.put("paidOrders", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE status = 1", Long.class));
        data.put("pendingOrders", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE status = 0", Long.class));
        data.put("cancelledOrders", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE status = 2", Long.class));

        long since = System.currentTimeMillis() - days * 24L * 3600 * 1000;
        List<Map<String, Object>> daily = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(FROM_UNIXTIME(order_time/1000), '%Y-%m-%d') AS day, " +
                        "COALESCE(SUM(product_price * product_num),0) AS gmv, COUNT(*) AS orders " +
                        "FROM `order` WHERE status IN (1,3) AND order_time > ? " +
                        "GROUP BY day ORDER BY day", since);
        data.put("daily", daily);
        return Result.success("success", data);
    }

    /** 订单分页（管理端，status 可选过滤） */
    @GetMapping("/page")
    public Result page(@RequestParam(value = "page", defaultValue = "1") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size,
                       @RequestParam(value = "status", required = false) Integer status) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.shop.order.pojo.Order> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(com.shop.order.pojo.Order::getStatus, status);
        }
        w.orderByDesc(com.shop.order.pojo.Order::getId);
        w.last("LIMIT " + (Math.max(page - 1, 0)) * size + ", " + size);
        List<com.shop.order.pojo.Order> rows = orderMapper.selectList(w);
        return Result.success("success", rows);
    }

    /** 售后分页（管理端，status 可选过滤：0待处理 1已退款 2已拒绝） */
    @GetMapping("/aftersale/page")
    public Result aftersalePage(@RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "size", defaultValue = "20") int size,
                                @RequestParam(value = "status", required = false) Integer status) {
        return Result.success("success", aftersaleService.pageAftersales(page, size, status));
    }

    /**
     * 售后审批（管理端写操作，order 端硬校验 ADMIN——网关直穿与 Feign 透传都拦）。
     * body: {aftersaleId, action: "approve"|"reject", reason(拒绝必填)}
     */
    @PostMapping("/aftersale/handle")
    public Result aftersaleHandle(@RequestBody Map<String, Object> body) {
        UserContext.Principal p = UserContext.get();
        if (p == null || !p.isAdmin()) {
            return Result.fail("无权处理售后（需管理员身份）", null);
        }
        String aftersaleId = body.get("aftersaleId") == null ? null : String.valueOf(body.get("aftersaleId"));
        String action = body.get("action") == null ? "" : String.valueOf(body.get("action"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        boolean approve = "approve".equals(action);
        if (!approve && !"reject".equals(action)) {
            return Result.fail("action 必须为 approve 或 reject", null);
        }
        return aftersaleService.handle(p.userId().intValue(), aftersaleId, approve, reason);
    }
}