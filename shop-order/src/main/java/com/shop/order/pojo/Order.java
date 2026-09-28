package com.shop.order.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单（V2 增量字段：status/payTime/seckillId）
 * 7 参构造器为迁移代码兼容保留；新代码请用 setter。
 */
@Data
@NoArgsConstructor
@TableName("`order`")
public class Order {

    /** 待支付 */
    public static final int STATUS_PENDING = 0;
    /** 已支付 */
    public static final int STATUS_PAID = 1;
    /** 已取消（超时未支付/用户取消），秒杀单取消时库存回滚 */
    public static final int STATUS_CANCELLED = 2;
    /** 已完成 */
    public static final int STATUS_DONE = 3;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String orderId;

    private Integer userId;

    private Integer productId;

    private Integer productNum;

    private Double productPrice;

    private Long orderTime;

    /** 订单状态机：0待支付 1已支付 2已取消 3已完成 */
    private Integer status;

    /** 支付时间戳(ms) */
    private Long payTime;

    /** 秒杀活动ID（秒杀单才有） */
    private Integer seckillId;

    /** 兼容迁移代码的 7 参构造（不含新增三列，由调用方 setter 补充） */
    public Order(Integer id, String orderId, Integer userId, Integer productId,
                 Integer productNum, Double productPrice, Long orderTime) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
        this.productNum = productNum;
        this.productPrice = productPrice;
        this.orderTime = orderTime;
    }
}