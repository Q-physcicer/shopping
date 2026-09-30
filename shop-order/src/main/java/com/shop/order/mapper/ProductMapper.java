package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.order.pojo.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

        @Select("select product_id from product")
        List<Integer> selectIds();

        /** P10 乐观锁扣库存（原走 XML mybatis/ProductMapper.xml，XML 加载机制在当前运行环境失效，改注解 SQL 兜底） */
        @org.apache.ibatis.annotations.Update("update product set product_num = product_num - #{saleNum}, " +
                "version = version + 1, product_sales = product_sales + #{saleNum} " +
                "where product_id = #{productId} and product_num >= #{saleNum} and version = #{currentVersion}")
        int updateStockByIdAndVersion(@Param("productId") Integer productId,
                                      @Param("saleNum") Integer saleNum,
                                      @Param("currentVersion") int currentVersion);

        /** P2：订单超时取消，普通商品库存+销量回滚（跨域写共享库，P4 改 ProductClient.restock 后替换） */
        @org.apache.ibatis.annotations.Update("update product set product_num = product_num + #{num}, " +
                "product_sales = product_sales - #{num}, version = version + 1 where product_id = #{productId}")
        int rollbackStock(@Param("productId") Integer productId, @Param("num") Integer num);
}