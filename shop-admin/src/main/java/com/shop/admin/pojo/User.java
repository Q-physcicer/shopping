package com.shop.admin.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer userId;

    private String username;

    private String password;

    /** 角色：USER / ADMIN（V2 增量） */
    private String role;

    /** 注册时间（V3 增量；插入不传值，DB 默认 CURRENT_TIMESTAMP 填充） */
    private java.time.LocalDateTime createdAt;

}