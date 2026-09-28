package com.shop.order.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("seckill_product")
public class SeckillProduct implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer seckillId;

    private Integer productId;

    private Double seckillPrice;

    @Version // 乐观锁版本号
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer version;

    @TableLogic // 逻辑删除字段
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer deleted;

    private Integer seckillStock;

    private Integer timeId;

    /** 以下三个字段在 seckill_product 表中不存在（继承遗留），显式排除防止 MP selectById 生成非法列 */
    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;
}
