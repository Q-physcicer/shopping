package com.shopping.vo;

import com.shopping.pojo.SeckillProduct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillProductVo extends SeckillProduct implements Serializable {

    private String productName;

    private Double productPrice;

    private String productPicture;

    private Long startTime;

    private Long endTime;


}
