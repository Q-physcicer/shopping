package com.shop.feign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后申请请求（P8 仅退款；user 身份经 FeignIdentityInterceptor 透传，不随 body 传输）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AftersaleApplyRequest {

    private String orderId;

    private Integer productId;

    private String reason;
}