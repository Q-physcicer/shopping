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
@TableName("product_picture")
public class ProductPicture {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer productId;

    private String productPicture;

    private String intro;
}