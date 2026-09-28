package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.order.pojo.Order;
import com.shop.order.vo.OrderVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("select `order`.*, `order`.order_time as orderTime ,product.product_name as productName, product.product_picture as productPicture " +
            "from `order`, product where `order`.product_id = product.product_id and `order`.user_id = #{userId} " +
            "ORDER BY order_time desc"
    )
    List<OrderVo> getOrderVoByUserId(Integer userId);

    /**
     * 超时取消订单 CAS：只有仍处于【待支付】才置【已取消】，支付与取消竞争时只赢一个。
     * @return 影响行数（0 表示订单已支付/已被处理，本次取消无效）
     */
    @Update("UPDATE `order` SET `status` = 2 WHERE `order_id` = #{orderId} AND `status` = 0")
    int cancelIfPending(@Param("orderId") String orderId);

    /** 模拟支付成功 CAS：待支付 → 已支付，落支付时间 */
    @Update("UPDATE `order` SET `status` = 1, `pay_time` = #{payTime} WHERE `order_id` = #{orderId} AND `status` = 0")
    int payIfPending(@Param("orderId") String orderId, @Param("payTime") Long payTime);
}