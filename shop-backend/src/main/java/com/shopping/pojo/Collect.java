package com.shopping.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

@Data
@TableName("collect")
public class Collect {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer productId;

    private Long collectTime;
}