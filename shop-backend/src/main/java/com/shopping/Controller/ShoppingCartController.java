package com.shopping.Controller;


import com.shopping.service.impl.ShoppingCartServiceImpl;
import com.shopping.util.Result;
import com.shopping.util.ResultMessage;
import com.shopping.vo.CartVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Description: 购物车控制类
 */
@RestController
@RequestMapping("/cart")
public class ShoppingCartController {
        
        @Autowired
        private ShoppingCartServiceImpl csi;

        /**
         * 获取购物车信息
         *
         * @param userId
         * @return
         */
        @GetMapping("/user/{userId}") //  获取购物车信息
        public Result cart(@PathVariable String userId) {
                List<CartVo> carts = csi.getCartByUserId(userId);
                return Result.success("欢迎！！",carts);
        }

        /**
         * 添加购物车
         *
         * @param productId
         * @param userId
         * @return
         */
        @PostMapping("/product/user/{productId}/{userId}") // 添加购物车
        public ResultMessage cart(@PathVariable String productId, @PathVariable String userId) {
                ResultMessage resultMessage = new ResultMessage();
                CartVo cartVo = csi.addCart(productId, userId);
                if (cartVo != null) {
                        if (cartVo.isUpdateNum()) {
                                resultMessage.success("002", "添加购物车成功", cartVo.getUpdateMessage());
                        }
                        resultMessage.success("001", "添加购物车成功", cartVo);
                } else {
                        resultMessage.success("002", "该商品已经在购物车，数量+1");
                }
                return resultMessage;
        }

        @PutMapping("/user/num/{cartId}/{userId}/{num}") // 修改购物车商品数量
        public Result cart(@PathVariable String cartId, @PathVariable String userId, @PathVariable String num) {
                csi.updateCartNum(cartId, userId, num);
                return Result.success("更新成功");
        }

        @DeleteMapping("/user/{cartId}/{userId}") //  删除购物车商品
        public Result deleteCart(@PathVariable String cartId, @PathVariable String userId) {
                csi.deleteCart(cartId, userId);
                return Result.success("删除成功");
        }
}
