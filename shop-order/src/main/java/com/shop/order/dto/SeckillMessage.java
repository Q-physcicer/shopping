package com.shop.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀下单消息（product 生产端 Map 结构对应）：
 * {seckillId, userId, messageId}，messageId = seckillId:userId
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    private String seckillId;

    private String userId;

    private String messageId;
}