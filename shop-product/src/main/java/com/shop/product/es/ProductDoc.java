package com.shop.product.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 商品搜索索引文档（product_index）。
 * 分词依赖服务端 ES 的 analysis-ik 插件（ik_max_word 建索引 / ik_smart 检索）。
 */
@Data
@Document(indexName = "product_index")
public class ProductDoc {

    @Id
    private Integer productId;

    /** 分词索引：名称权重最高的检索字段 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String productName;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String productTitle;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String productIntro;

    @Field(type = FieldType.Integer)
    private Integer categoryId;

    @Field(type = FieldType.Keyword)
    private String productPicture;

    @Field(type = FieldType.Double)
    private Double productPrice;

    @Field(type = FieldType.Double)
    private Double productSellingPrice;

    @Field(type = FieldType.Integer)
    private Integer productSales;

    /** 有货可售才入索引（num>0），售罄商品不出现在搜索结果 */
    @Field(type = FieldType.Integer)
    private Integer productNum;
}