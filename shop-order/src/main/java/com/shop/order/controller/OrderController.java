package com.shop.order.controller;

import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import com.shop.feign.OrderCreateRequest;
import com.shop.order.service.impl.OrderServiceImpl;
import com.shop.order.vo.OrderVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单模块（P1 JWT 改造 + P4 Agent 直购端点）
 *
 * @Description: 订单模块
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderServiceImpl osi;

    /**
     * 添加订单（购物车结算，P0-6 新契约）。
     * body: {items:[{productId,num}], addressId}；价格由服务端回查商品表，客户端 price 一律忽略。
     */
    @PostMapping("")
    public Result addOrder(@RequestBody OrderCreateRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Result.fail("下单商品不能为空", null);
        }
        String orderId = osi.addOrderFromRequest(request, requireUserId());
        return Result.success("下单成功，30 分钟内未支付将自动取消", java.util.Map.of("orderId", orderId));
    }

    /** 获取当前用户订单（按状态分组） */
    @GetMapping("")
    public Result getOrder() {
        List<List<OrderVo>> orders = osi.getOrder(requireUserId());
        return Result.success("success", orders);
    }

    /**
     * Agent 直购下单（P4）：AI 客服代客直接购买，不经购物车。
     * body 仅含 productId/num；用户身份由 FeignIdentityInterceptor 从上游透传的 X-User-Id 取。
     */
    @PostMapping("/direct")
    public Result placeOrderDirect(@RequestBody Map<String, Object> body) {
        Integer userId = requireUserId();
        Integer productId = Integer.valueOf(String.valueOf(body.get("productId")));
        Integer num = body.get("num") == null ? 1 : Integer.valueOf(String.valueOf(body.get("num")));
        if (num < 1 || num > 5) {
            return Result.fail("购买数量须在 1-5 之间", null);
        }
        String orderId = osi.placeDirectOrder(userId, productId, num);
        return Result.success("下单成功（30 分钟内未支付将自动取消）", Map.of("orderId", orderId));
    }

    private Integer requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return uid.intValue();
    }
}