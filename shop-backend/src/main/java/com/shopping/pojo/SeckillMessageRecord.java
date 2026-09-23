package com.shopping.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("seckill_message_record")
@AllArgsConstructor
@NoArgsConstructor
public class SeckillMessageRecord implements Serializable {

    @TableId(type = IdType.AUTO) // 使用数据库自增ID
    private Long id;

    private String messageId;

    private String userId;

    private String seckillId;

    private String status;

    private Integer retryCount;

    private String errorMessage;

    private Date createdAt;

    private Date updatedAt;

    private static final long serialVersionUID = 1L;
}
