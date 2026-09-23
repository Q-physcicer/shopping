package com.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.pojo.Collect;
import com.shopping.pojo.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollectMapper extends BaseMapper<Collect> {

    @Select("select product.* from product, collect where collect.user_id = #{userId} and collect.product_id = product.product_id")
    List<Product> getCollect(String userId);
}