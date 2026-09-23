package com.shopping.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("shopping_cart")
public class ShoppingCart {
        @TableId(type = IdType.AUTO)
        private Integer id;

        private Integer userId;

        private Integer productId;

        private Integer num;

        @Version // 乐观锁版本号注解
        @TableField(fill = FieldFill.INSERT) // 插入时自动填充初始值
        private Integer version; // 版本号，用于乐观锁控制

}
