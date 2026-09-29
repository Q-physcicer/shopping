package com.shop.user.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收货地址（购物端个人中心，表 user_address）。
 * 字段与表列一一对应（MyBatis-Plus 非法列 SQL 历史坑：勿加表外字段）。
 */
@Data
@NoArgsConstructor
@TableName("user_address")
public class UserAddress {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户 */
    private Integer userId;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址 */
    private String detailAddress;

    /** 是否默认地址 0否 1是 */
    private Integer isDefault;

    /** 创建时间戳(ms) */
    private Long createdTime;

    /** 更新时间戳(ms) */
    private Long updatedTime;
}
