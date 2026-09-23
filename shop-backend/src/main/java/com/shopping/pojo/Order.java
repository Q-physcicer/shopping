package com.shopping.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String orderId;

    private Integer userId;

    private Integer productId;

    private Integer productNum;

    private Double productPrice;

    private Long orderTime;
}