package com.shopping.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

@Data
@TableName("carousel")
public class Carousel {

    @TableId(value = "carousel_id", type = IdType.AUTO)
    private Integer carouselId;

    @TableField("img_path")
    private String imgPath;

    @TableField("describes")
    private String describes;
}