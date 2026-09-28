package com.shop.order.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后申请（P8 仅退款闭环，表 aftersale_record）。
 * 字段与表列一一对应（MyBatis-Plus 非法列 SQL 历史坑：勿加表外字段）。
 */
@Data
@NoArgsConstructor
@TableName("aftersale_record")
public class AftersaleRecord {

    /** 待处理 */
    public static final int STATUS_PENDING = 0;
    /** 同意（退款完成，终态） */
    public static final int STATUS_APPROVED = 1;
    /** 已拒绝（终态，用户可重新申请新单） */
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 售后单号（IdWorker 生成） */
    private String aftersaleId;

    /** 订单号（order.order_id） */
    private String orderId;

    /** 订单行ID（order.id） */
    private Integer orderRowId;

    private Integer userId;

    private Integer productId;

    /** 售后数量（申请时订单行快照） */
    private Integer productNum;

    /** 退款金额 = 行价格*数量（申请时快照） */
    private Double refundAmount;

    /** 用户申请理由 */
    private String reason;

    /** 0待处理 1同意(退款完成) 2拒绝 */
    private Integer status;

    /** 拒绝理由（拒绝时必填） */
    private String rejectReason;

    /** 申请时间戳(ms) */
    private Long applyTime;

    /** 处理时间戳(ms) */
    private Long handleTime;

    /** 处理管理员 userId */
    private Integer handlerId;
}