package com.shop.cart.controller;


import com.shop.cart.service.impl.ShoppingCartServiceImpl;
import com.shop.common.context.UserContext;
import com.shop.common.util.Result;
import com.shop.common.util.ResultMessage;
import com.shop.cart.vo.CartVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制类（P1 越权修复：身份一律从网关注入的 X-User-Id 取，路径不再传 userId）
 *
 * @Description: 购物车控制类
 */
@RestController
@RequestMapping("/cart")
public class ShoppingCartController {

        @Autowired
        private ShoppingCartServiceImpl csi;

        /** 获取当前用户购物车 */
        @GetMapping("/user")
        public Result cart() {
                String userId = requireUserId();
                List<CartVo> carts = csi.getCartByUserId(userId);
                return Result.success("欢迎！！",carts);
        }

        /** 添加购物车 */
        @PostMapping("/product/{productId}")
        public ResultMessage cart(@PathVariable String productId) {
                String userId = requireUserId();
                ResultMessage resultMessage = new ResultMessage();
                CartVo cartVo = csi.addCart(productId, userId);
                if (cartVo != null) {
                        if (cartVo.isUpdateNum()) {
                                resultMessage.success("002", "添加购物车成功", cartVo.getUpdateMessage());
                                return resultMessage;   // 修复 fall-through：原缺 return 导致 002 被 001 覆盖
                        }
                        if ("该商品购物车达到上限".equals(cartVo.getUpdateMessage())) {
                                // 限购 5 上限（原走异常→code=0，前端 case "003" 死分支，按钮禁用逻辑失效）
                                resultMessage.success("003", cartVo.getUpdateMessage(), cartVo);
                                return resultMessage;
                        }
                        resultMessage.success("001", "添加购物车成功", cartVo);
                } else {
                        resultMessage.success("002", "该商品已经在购物车，数量+1");
                }
                return resultMessage;
        }

        /** 修改购物车商品数量 */
        @PutMapping("/user/num/{cartId}/{num}")
        public Result cart(@PathVariable String cartId, @PathVariable String num) {
                String userId = requireUserId();
                csi.updateCartNum(cartId, userId, num);
                return Result.success("更新成功");
        }

        /** 删除购物车商品 */
        @DeleteMapping("/user/{cartId}")
        public Result deleteCart(@PathVariable String cartId) {
                String userId = requireUserId();
                csi.deleteCart(cartId, userId);
                return Result.success("删除成功");
        }

        /** 统一取登录用户；网关保证业务路径必然携带身份，兜底防御性校验 */
        private String requireUserId() {
                Long uid = UserContext.getUserId();
                if (uid == null) {
                        throw new com.shop.common.exception.XmException(
                                        com.shop.common.exception.ExceptionEnum.GET_USER_NOT_FOUND);
                }
                return String.valueOf(uid);
        }
}