package com.shopping.pojo;

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
    private Integer version;

    @TableLogic // 逻辑删除字段
    private Integer deleted;

    private Integer seckillStock;

    private Integer timeId;

    @TableField(fill = FieldFill.INSERT)
    private Long createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}
