package com.shop.order.controller;

import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import com.shop.order.service.impl.AftersaleServiceImpl;
import com.shop.order.vo.AftersaleVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 售后接口（P8 仅退款闭环）。
 * 用户身份一律 UserContext（网关注入的 X-User-Id），禁止路径传 userId。
 */
@RestController
@RequestMapping("/order/aftersale")
public class AftersaleController {

    @Autowired
    private AftersaleServiceImpl aftersaleService;

    /** 提交售后申请（body: orderId / productId / reason） */
    @PostMapping("/apply")
    public Result apply(@RequestBody Map<String, Object> body) {
        Integer userId = requireUserId();
        String orderId = body.get("orderId") == null ? null : String.valueOf(body.get("orderId")).trim();
        Integer productId = body.get("productId") == null ? null
                : Integer.valueOf(String.valueOf(body.get("productId")));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason")).trim();
        return aftersaleService.apply(userId, orderId, productId, reason);
    }

    /** 我的售后列表（无记录返回空列表） */
    @GetMapping("/my")
    public Result my() {
        Integer userId = requireUserId();
        List<AftersaleVo> list = aftersaleService.myAftersales(userId);
        return Result.success("success", list);
    }

    private Integer requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return uid.intValue();
    }
}