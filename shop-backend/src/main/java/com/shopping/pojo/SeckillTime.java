package com.shopping.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("seckill_time")
public class SeckillTime {

    @TableId(type = IdType.AUTO)
    private Integer timeId;

    private Long startTime;

    private Long endTime;

}
