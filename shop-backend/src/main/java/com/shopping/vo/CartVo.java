package com.shopping.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartVo {

        private Integer id;

        private Integer productId;

        private String productName;

        private String productImg;

        private Double price;

        private Integer num;

        private Integer maxNum;

        private boolean check;

        private boolean updateNum;

        private String updateMessage;
}
