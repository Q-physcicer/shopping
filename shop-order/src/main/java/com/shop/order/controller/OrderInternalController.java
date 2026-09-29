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
 * ⚠️ /order/internal/** 原经网关对外可达（路由 /order/**），现已在网关层 403；
 * 网关之外（业务端口暴露/内部直连）的第二道闸：**所有**读写接口一律硬校验 ADMIN
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
    @Autowired
    private com.shop.order.service.impl.UserMessageServiceImpl messageService;

    /** 统一 ADMIN 硬校验（防业务端口暴露；网关 403 为第一道，此处为纵深防御） */
    private Result requireAdmin() {
        UserContext.Principal p = UserContext.get();
        if (p == null || !p.isAdmin()) {
            return Result.fail("无权访问内部接口（需管理员身份）", null);
        }
        return null;
    }

    /** GMV 概览：总 GMV / 总订单数 / 待支付数 / 已取消数 + 近 N 日每日曲线 */
    @GetMapping("/stats/gmv")
    public Result gmv(@RequestParam(value = "days", defaultValue = "7") int days) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
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

    /** 订单分页（管理端，status/orderId 可选过滤；返回 {list,total}） */
    @GetMapping("/page")
    public Result page(@RequestParam(value = "page", defaultValue = "1") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size,
                       @RequestParam(value = "status", required = false) Integer status,
                       @RequestParam(value = "orderId", required = false) String orderId) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(page, 1);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.shop.order.pojo.Order> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(com.shop.order.pojo.Order::getStatus, status);
        }
        if (orderId != null && !orderId.isBlank()) {
            w.like(com.shop.order.pojo.Order::getOrderId, orderId.trim());
        }
        w.orderByDesc(com.shop.order.pojo.Order::getId);
        Long total = orderMapper.selectCount(w);
        w.last("LIMIT " + (page - 1) * size + ", " + size);
        List<com.shop.order.pojo.Order> rows = orderMapper.selectList(w);
        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", total);
        return Result.success("success", data);
    }

    /** 售后分页（管理端，status 可选过滤：0待处理 1已退款 2已拒绝） */
    @GetMapping("/aftersale/page")
    public Result aftersalePage(@RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "size", defaultValue = "20") int size,
                                @RequestParam(value = "status", required = false) Integer status) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success("success", aftersaleService.pageAftersales(page, size, status));
    }

    /**
     * 售后审批（管理端写操作，order 端硬校验 ADMIN——网关直穿与 Feign 透传都拦）。
     * body: {aftersaleId, action: "approve"|"reject", reason(拒绝必填)}
     */
    @PostMapping("/aftersale/handle")
    public Result aftersaleHandle(@RequestBody Map<String, Object> body) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        UserContext.Principal p = UserContext.get();
        String aftersaleId = body.get("aftersaleId") == null ? null : String.valueOf(body.get("aftersaleId"));
        String action = body.get("action") == null ? "" : String.valueOf(body.get("action"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        boolean approve = "approve".equals(action);
        if (!approve && !"reject".equals(action)) {
            return Result.fail("action 必须为 approve 或 reject", null);
        }
        return aftersaleService.handle(p.userId().intValue(), aftersaleId, approve, reason);
    }

    /** 管理端标记完成（CAS 1→3；原状态 3 无任何代码路径可达，属永不可达状态） */
    @PostMapping("/done")
    public Result markDone(@RequestBody Map<String, Object> body) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        String orderId = body.get("orderId") == null ? "" : String.valueOf(body.get("orderId")).trim();
        if (orderId.isEmpty()) {
            return Result.fail("orderId 不能为空", null);
        }
        int updated = orderMapper.markDoneIfPaid(orderId);
        if (updated == 0) {
            return Result.fail("仅已支付订单可标记完成", null);
        }
        // 状态变更通知用户
        List<com.shop.order.pojo.Order> rows = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.shop.order.pojo.Order>()
                        .eq(com.shop.order.pojo.Order::getOrderId, orderId));
        if (!rows.isEmpty()) {
            messageService.push(rows.get(0).getUserId(), com.shop.order.pojo.UserMessage.TYPE_ORDER_DONE, "订单完成",
                    "订单 " + orderId + " 已完成，感谢您的购买！");
        }
        return Result.success("已标记完成");
    }
}