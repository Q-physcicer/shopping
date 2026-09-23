package com.shopping.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Integer categoryId;

    private String categoryName;
}