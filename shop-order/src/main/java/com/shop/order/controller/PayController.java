package com.shop.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import com.shop.common.util.IdWorker;
import com.shop.order.mapper.OrderMapper;
import com.shop.order.mapper.PaymentRecordMapper;
import com.shop.order.pojo.Order;
import com.shop.order.pojo.PaymentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟收银台（P6）：
 * GET  /pay/mock/{orderId} —— 拉起收银台信息（金额/状态）
 * POST /pay/mock/{orderId} —— 模拟支付成功：CAS(待支付→已支付) 与超时取消互斥，只赢一个；
 *                              落 payment_record 流水；订单号雪花生成支付单号。
 */
@RestController
@RequestMapping("/pay")
public class PayController {

    private static final Logger log = LoggerFactory.getLogger(PayController.class);

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private PaymentRecordMapper paymentMapper;

    private final IdWorker idWorker = new IdWorker(2, 2);

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
        Integer status = rows.get(0).getStatus();
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("amount", amount);
        data.put("status", status);   // 0=可支付，1=已支付（收银台展示已支付态）
        data.put("items", rows.size());
        return Result.success("success", data);
    }

    /** 模拟支付成功回调 */
    @PostMapping("/mock/{orderId}")
    public Result pay(@PathVariable String orderId) {
        Integer uid = requireUserId();
        List<Order> rows = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderId, orderId).eq(Order::getUserId, uid));
        if (rows.isEmpty()) {
            throw new XmException(ExceptionEnum.GET_ORDER_NOT_FOUND);
        }
        double amount = rows.stream().mapToDouble(o -> o.getProductPrice() * o.getProductNum()).sum();

        // CAS：与 OrderTimeoutListener 的 cancelIfPending 互斥
        int updated = orderMapper.payIfPending(orderId, System.currentTimeMillis());
        if (updated == 0) {
            return Result.fail("订单不可支付（可能已支付/已取消）", null);
        }

        // 流水
        PaymentRecord record = new PaymentRecord();
        record.setPayNo("MOCK" + idWorker.nextId());
        record.setOrderId(orderId);
        record.setUserId(uid);
        record.setAmount(amount);
        record.setPayChannel("MOCK");
        record.setStatus(1);
        record.setCreatedAt(new Date());
        paymentMapper.insert(record);

        log.info("[Pay] 模拟支付成功 orderId={} userId={} amount={}", orderId, uid, amount);
        return Result.success("支付成功", Map.of("payNo", record.getPayNo(), "amount", amount));
    }

    private Integer requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return uid.intValue();
    }
}