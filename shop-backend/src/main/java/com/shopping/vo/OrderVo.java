package com.shopping.vo;


import com.shopping.pojo.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderVo extends Order {

        private String productName;

        private String productPicture;

        private Long orderTime;

}
