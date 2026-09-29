package com.shop.admin.pojo;

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

    /** 场次来源：auto=每日 15:00 定时重建（会被清），manual=管理端手动建（重建不清理） */
    private String source;

    /** 兼容旧 3 参构造 */
    public SeckillTime(Integer timeId, Long startTime, Long endTime) {
        this.timeId = timeId;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
