package com.shop.cart.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.cart.mapper.ProductMapper;
import com.shop.cart.mapper.ShoppingCartMapper;
import com.shop.cart.pojo.Product;
import com.shop.cart.pojo.ShoppingCart;
import com.shop.cart.vo.CartVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper, ShoppingCart> {

        @Autowired
        private ProductMapper productMapper;

        public List<CartVo> getCartByUserId(String userId) {
                List<ShoppingCart> list = null;
                List<CartVo> cartVoList = new ArrayList<>();
                try {
                        list = this.lambdaQuery().eq(ShoppingCart::getUserId, Integer.parseInt(userId)).list();
                        for (ShoppingCart c : list) {
                                cartVoList.add(getCartVo(c));
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                        throw new XmException(ExceptionEnum.GET_CART_ERROR);
                }
                return cartVoList;
        }

        @Transactional
        public CartVo addCart(String productId, String userId) {
                ShoppingCart cart = new ShoppingCart();
                cart.setUserId(Integer.parseInt(userId));
                cart.setProductId(Integer.parseInt(productId));
                ShoppingCart one = this.lambdaQuery().eq(ShoppingCart::getUserId, cart.getUserId())
                        .eq(ShoppingCart::getProductId, cart.getProductId()).one();

                if (one != null) {
                        if (one.getNum() >= 5) {
                                // 限购超限不再抛异常（原走 XmException→code=0，前端 case "003" 成死分支，
                                // 按钮永不置灰）；改为返回 num=5 的实体让 controller 拼 "003" 业务码
                                CartVo limited = getCartVo(one);
                                limited.setUpdateNum(false);
                                limited.setNum(5);
                                limited.setUpdateMessage("该商品购物车达到上限");
                                return limited;
                        }
                        Integer version = one.getVersion();
                        one.setNum(one.getNum() + 1);
                        boolean updateResult = this.lambdaUpdate().eq(ShoppingCart::getId, one.getId())
                                .eq(ShoppingCart::getVersion, version).set(ShoppingCart::getNum, one.getNum())
                                .set(ShoppingCart::getVersion, version + 1).update();
                        if (!updateResult) {
                                CartVo cartVo = new CartVo();
                                cartVo.setUpdateMessage("并发修改失败，请重试！");
                                cartVo.setUpdateNum(false);
                                return cartVo;
                        } else {
                                return null;
                        }
                } else {
                        cart.setNum(1);
                        cart.setVersion(1);
                        this.save(cart);
                        return getCartVo(cart);
                }
        }

        private CartVo getCartVo(ShoppingCart cart) {
                Product product = productMapper.selectById(cart.getProductId());
                CartVo cartVo = new CartVo();
                cartVo.setId(cart.getId());
                cartVo.setProductId(cart.getProductId());
                cartVo.setProductName(product.getProductName());
                cartVo.setProductImg(product.getProductPicture());
                cartVo.setPrice(product.getProductSellingPrice());
                cartVo.setNum(cart.getNum());
                cartVo.setMaxNum(5);
                cartVo.setCheck(false);
                return cartVo;
        }

        public void updateCartNum(String cartId, String userId, String num) {
                // 数量边界校验（原缺陷：负数/超量可落库，结算时生成负数订单并反向增加库存）
                int n;
                try {
                        n = Integer.parseInt(num);
                } catch (NumberFormatException e) {
                        throw new XmException("数量格式错误");
                }
                if (n < 1 || n > 5) {
                        throw new XmException("购买数量须在 1-5 之间");
                }
                ShoppingCart cart = new ShoppingCart();
                cart.setId(Integer.parseInt(cartId));
                cart.setUserId(Integer.parseInt(userId));
                cart.setNum(n);
                try {
                        boolean updateResult = this.updateById(cart);
                        if (!updateResult) {
                                throw new XmException(ExceptionEnum.UPDATE_CART_ERROR);
                        }
                } catch (XmException xe) {
                        throw xe;
                } catch (Exception e) {
                        e.printStackTrace();
                        throw new XmException(ExceptionEnum.UPDATE_CART_ERROR);
                }
        }

        public void deleteCart(String cartId, String userId) {
                try {
                        boolean deleteResult = this.lambdaUpdate().eq(ShoppingCart::getId, Integer.parseInt(cartId))
                                .eq(ShoppingCart::getUserId, Integer.parseInt(userId)).remove();
                        if (!deleteResult) {
                                throw new XmException(ExceptionEnum.DELETE_CART_ERROR);
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                        throw new XmException(ExceptionEnum.DELETE_CART_ERROR);
                }
        }
}
