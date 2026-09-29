package com.shop.order.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户消息（购物端消息中心，表 user_message）。
 * 字段与表列一一对应（MyBatis-Plus 非法列 SQL 历史坑：勿加表外字段）。
 */
@Data
@NoArgsConstructor
@TableName("user_message")
public class UserMessage {

    /** 消息类型：支付成功 */
    public static final String TYPE_ORDER_PAID = "order_paid";
    /** 消息类型：订单取消 */
    public static final String TYPE_ORDER_CANCELLED = "order_cancelled";
    /** 消息类型：售后结果 */
    public static final String TYPE_AFTERSALE = "aftersale";
    /** 消息类型：订单完成 */
    public static final String TYPE_ORDER_DONE = "order_done";
    /** 消息类型：下单成功 */
    public static final String TYPE_ORDER_CREATED = "order_created";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户 */
    private Integer userId;

    /** 消息类型 */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 0未读 1已读 */
    private Integer isRead;

    /** 创建时间戳(ms) */
    private Long createdTime;
}
