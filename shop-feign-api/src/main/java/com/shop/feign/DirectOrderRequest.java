package com.shop.feign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 直购下单请求（P4；user 身份经 FeignIdentityInterceptor 透传，不随 body 传输）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DirectOrderRequest {

    private Integer productId;

    private Integer num;
}