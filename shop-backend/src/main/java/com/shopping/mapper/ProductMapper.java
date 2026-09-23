package com.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.pojo.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

        @Select("select product_id from product")
        List<Integer> selectIds();

        int updateStockByIdAndVersion(@Param("productId") Integer productId,
                                      @Param("saleNum") Integer saleNum,
                                      @Param("currentVersion") int currentVersion);
}