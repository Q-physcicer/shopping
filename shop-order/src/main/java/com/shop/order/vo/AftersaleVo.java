package com.shop.order.vo;

import com.shop.order.pojo.AftersaleRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后申请展示 VO（联表商品名/图片，仿 OrderVo）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AftersaleVo extends AftersaleRecord {

    private String productName;

    private String productPicture;
}