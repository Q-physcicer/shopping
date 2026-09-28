package com.shop.order.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 模拟支付流水（P6）
 */
@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payNo;

    private String orderId;

    private Integer userId;

    private Double amount;

    /** MOCK */
    private String payChannel;

    /** 0发起 1成功 2失败 */
    private Integer status;

    private Date createdAt;
}