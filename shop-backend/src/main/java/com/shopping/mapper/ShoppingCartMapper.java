package com.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.pojo.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {

        /**
         * 根据ID和版本号更新购物车信息
         * @param cart 购物车对象
         * @return 更新影响的行数
         */
        int updateCartByIdAndVersion(@Param("cart") ShoppingCart cart);
}
