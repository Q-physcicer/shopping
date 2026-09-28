package com.shop.product.vo;

import lombok.Data;

/**
 * 搜索结果条目（P3）：productName/productTitle/productIntro 可能含 <em> 高亮标记
 */
@Data
public class SearchProductVo {

    private Integer productId;

    /** 可能携带 &lt;em&gt; 高亮 */
    private String productName;

    private String productTitle;

    private String productIntro;

    private String productPicture;

    private Double productPrice;

    private Double productSellingPrice;

    private Integer productSales;

    private Integer categoryId;
}