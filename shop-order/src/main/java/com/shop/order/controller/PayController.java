package com.shop.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import com.shop.order.mapper.OrderMapper;
import com.shop.order.pojo.Order;
import com.shop.order.service.impl.OrderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟收银台（P6，P0 事务化重构）：
 * GET  /pay/mock/{orderId} —— 拉起收银台信息（金额/状态/收件信息快照）
 * POST /pay/mock/{orderId} —— 模拟支付成功：核心三连写收敛到 OrderServiceImpl.payMock（@Transactional），
 *                              CAS(待支付→已支付) 与超时取消互斥只赢一个，流水/消息同事务落库。
 */
@RestController
@RequestMapping("/pay")
public class PayController {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderServiceImpl osi;

    /** 收银台信息 */
    @GetMapping("/mock/{orderId}")
    public Result cashier(@PathVariable String orderId) {
        Integer uid = requireUserId();
        List<Order> rows = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderId, orderId).eq(Order::getUserId, uid));
        if (rows.isEmpty()) {
            throw new XmException(ExceptionEnum.GET_ORDER_NOT_FOUND);
        }
        double amount = rows.stream().mapToDouble(o -> o.getProductPrice() * o.getProductNum()).sum();
        Order first = rows.get(0);
        Integer status = first.getStatus();
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("amount", amount);
        data.put("status", status);   // 0=可支付，1=已支付（收银台展示已支付态）
        data.put("items", rows.size());
        // P0-6：收件信息快照（随单落库后展示；存量老单无快照为 null，前端不渲染该区）
        data.put("receiverName", first.getReceiverName());
        data.put("receiverPhone", first.getReceiverPhone());
        data.put("receiverAddress", first.getReceiverAddress());
        return Result.success("success", data);
    }

    /** 模拟支付成功回调（核心逻辑在 OrderServiceImpl.payMock，@Transactional 包裹三连写） */
    @PostMapping("/mock/{orderId}")
    public Result pay(@PathVariable String orderId) {
        Integer uid = requireUserId();
        Map<String, Object> data = osi.payMock(uid, orderId);
        return Result.success("支付成功", data);
    }

    private Integer requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return uid.intValue();
    }
}
