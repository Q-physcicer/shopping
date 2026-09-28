package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.order.pojo.AftersaleRecord;
import com.shop.order.pojo.Order;
import com.shop.order.vo.AftersaleVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 售后申请 Mapper（P8 仅退款闭环）。
 */
@Mapper
public interface AftersaleMapper extends BaseMapper<AftersaleRecord> {

    /** 用户售后列表（联商品名，最近优先） */
    @Select("SELECT ar.*, p.product_name AS productName, p.product_picture AS productPicture " +
            "FROM aftersale_record ar LEFT JOIN product p ON ar.product_id = p.product_id " +
            "WHERE ar.user_id = #{userId} ORDER BY ar.id DESC")
    List<AftersaleVo> listByUser(@Param("userId") Integer userId);

    /**
     * 申请事务内锁父订单行（FOR UPDATE）：串行化同一订单行的并发售后服务，
     * MySQL 无 partial index 不能对 (order_row_id, status=0) 建唯一键的替代方案。
     */
    @Select("SELECT * FROM `order` WHERE order_id = #{orderId} AND product_id = #{productId} AND user_id = #{userId} FOR UPDATE")
    Order lockOrderRow(@Param("orderId") String orderId, @Param("productId") Integer productId, @Param("userId") Integer userId);

    /** 管理端分页计数（status 可选） */
    @Select("<script>SELECT COUNT(*) FROM aftersale_record " +
            "<where><if test='status != null'>status = #{status}</if></where></script>")
    Long countByCond(@Param("status") Integer status);

    /** 管理端分页列表（status 可选，最近优先） */
    @Select("<script>SELECT ar.*, p.product_name AS productName, p.product_picture AS productPicture " +
            "FROM aftersale_record ar LEFT JOIN product p ON ar.product_id = p.product_id " +
            "<where><if test='status != null'>ar.status = #{status}</if></where> " +
            "ORDER BY ar.id DESC LIMIT #{offset}, #{size}</script>")
    List<AftersaleVo> pageByCond(@Param("status") Integer status, @Param("offset") int offset, @Param("size") int size);

    /**
     * 管理端同意（CAS）：仅待处理可流转为【同意·退款完成】。
     * @return 影响行数（0 表示已被处理/不存在，防双管理员并发审批）
     */
    @Update("UPDATE aftersale_record SET status = 1, handle_time = #{now}, handler_id = #{handlerId} " +
            "WHERE aftersale_id = #{aftersaleId} AND status = 0")
    int approveIfPending(@Param("aftersaleId") String aftersaleId, @Param("now") long now, @Param("handlerId") Integer handlerId);

    /** 管理端拒绝（CAS）：仅待处理可流转为【已拒绝】，拒绝理由必填 */
    @Update("UPDATE aftersale_record SET status = 2, reject_reason = #{reason}, handle_time = #{now}, handler_id = #{handlerId} " +
            "WHERE aftersale_id = #{aftersaleId} AND status = 0")
    int rejectIfPending(@Param("aftersaleId") String aftersaleId, @Param("reason") String reason, @Param("now") long now, @Param("handlerId") Integer handlerId);
}