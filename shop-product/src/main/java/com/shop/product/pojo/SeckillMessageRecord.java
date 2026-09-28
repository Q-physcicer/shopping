package com.shop.product.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 秒杀本地消息记录（product/order 双端共用的基础设施表）：
 * - product 端：抢购成功(预扣库存后)写入 SENT，MQ 发布确认失败置 FAILED
 * - order 端：消费时 CAS(SENT→PROCESSING→CONSUMED) 保证幂等，最终失败置 FAILED
 */
@Data
@TableName("seckill_message_record")
public class SeckillMessageRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务消息ID = seckillId:userId */
    private String messageId;

    private String userId;

    private String seckillId;

    /** SENT / PROCESSING / CONSUMED / FAILED */
    private String status;

    private Integer retryCount;

    private String errorMessage;

    private Date createdAt;

    private Date updatedAt;
}