package com.shopping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.ProductMapper;
import com.shopping.mapper.ShoppingCartMapper;
import com.shopping.pojo.Product;
import com.shopping.pojo.ShoppingCart;
import com.shopping.vo.CartVo;
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
                                throw new XmException(ExceptionEnum.ADD_CART_NUM_UPPER);
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
                ShoppingCart cart = new ShoppingCart();
                cart.setId(Integer.parseInt(cartId));
                cart.setUserId(Integer.parseInt(userId));
                cart.setNum(Integer.parseInt(num));
                try {
                        boolean updateResult = this.updateById(cart);
                        if (!updateResult) {
                                throw new XmException(ExceptionEnum.UPDATE_CART_ERROR);
                        }
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
