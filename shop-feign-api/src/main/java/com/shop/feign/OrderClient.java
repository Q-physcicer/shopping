package com.shop.feign;

import com.shop.common.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * order 服务客户端（chat Agent 代客直购 / P5 admin 订单管理消费）。
 */
@FeignClient(name = "shop-order", contextId = "orderClient")
public interface OrderClient {

    /** Agent 直接下单（不经购物车），订单落库后返回订单号 */
    @PostMapping("/order/direct")
    Result placeOrderDirect(@RequestBody DirectOrderRequest request);

    /** 当前用户订单列表（分组结构） */
    @GetMapping("/order")
    Result getMyOrders();

    /** GMV/订单统计（admin） */
    @GetMapping("/order/internal/stats/gmv")
    Result gmv(@RequestParam(value = "days", defaultValue = "7") int days);

    /** 订单分页（admin；返回 {list,total}） */
    @GetMapping("/order/internal/page")
    Result pageOrders(@RequestParam(value = "page", defaultValue = "1") int page,
                      @RequestParam(value = "size", defaultValue = "20") int size,
                      @RequestParam(value = "status", required = false) Integer status,
                      @RequestParam(value = "orderId", required = false) String orderId);

    /** 管理端标记订单完成（CAS 1→3） */
    @PostMapping("/order/internal/done")
    Result markOrderDone(@RequestBody java.util.Map<String, Object> body);

    // ---------------- 售后（P8 仅退款闭环） ----------------

    /** 用户提交售后申请（body: orderId/productId/reason） */
    @PostMapping("/order/aftersale/apply")
    Result applyAftersale(@RequestBody AftersaleApplyRequest request);

    /** 当前用户售后列表 */
    @GetMapping("/order/aftersale/my")
    Result getMyAftersales();

    /** 售后分页（admin，status 可选） */
    @GetMapping("/order/internal/aftersale/page")
    Result pageAftersales(@RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "size", defaultValue = "20") int size,
                          @RequestParam(value = "status", required = false) Integer status);

    /** 售后审批（admin 写操作，body: aftersaleId/action/reason） */
    @PostMapping("/order/internal/aftersale/handle")
    Result handleAftersale(@RequestBody java.util.Map<String, Object> body);
}