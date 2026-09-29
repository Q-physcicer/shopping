package com.shop.feign;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 购物车结算下单契约（P0-6）：
 * 客户端只提交 {items:[{productId,num}], addressId}；价格由服务端回查商品表，
 * 绝不信任客户端价格（防 0.01 元下单）。
 */
@Data
@NoArgsConstructor
public class OrderCreateRequest {

    /** 下单商品行（productId + num；price 字段即使传了也会被忽略） */
    private List<Item> items;

    /** 收货地址 ID（user 域，order 经 Feign 回查并落快照） */
    private Long addressId;

    @Data
    @NoArgsConstructor
    public static class Item {
        private Integer productId;
        private Integer num;
        /** 客户端价格仅用于前端展示对账，服务端一律忽略 */
        private Double price;
    }
}
