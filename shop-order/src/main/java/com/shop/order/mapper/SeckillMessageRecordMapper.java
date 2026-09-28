package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.order.pojo.SeckillMessageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeckillMessageRecordMapper extends BaseMapper<SeckillMessageRecord> {

    /**
     * 根据消息ID查询记录
     */
    SeckillMessageRecord findByMessageId(@Param("correlationId") String correlationId);

    /**
     * 根据消息ID更新记录
     */
    int updateByMessageId(@Param("record") SeckillMessageRecord record);

    /**
     * P2 幂等 CAS：status from → to，返回影响行数（0 表示被其它实例消费/状态不符）。
     * 消费端用它实现"抢占处理权"：SENT→CONSUMED 抢占成功才落库；失败后 CONSUMED→SENT 回退再重试。
     */
    @org.apache.ibatis.annotations.Update("UPDATE seckill_message_record SET `status` = #{to}, updated_at = NOW() " +
            "WHERE message_id = #{messageId} AND `status` = #{from}")
    int casUpdateStatus(@Param("messageId") String messageId, @Param("from") String from, @Param("to") String to);

    /** P2：消费失败回退状态并记数（PROCESSING→SENT / CONSUMED→SENT） */
    @org.apache.ibatis.annotations.Update("UPDATE seckill_message_record SET `status` = 'SENT', " +
            "retry_count = retry_count + 1, error_message = #{error}, updated_at = NOW() " +
            "WHERE message_id = #{messageId}")
    int markRetry(@Param("messageId") String messageId, @Param("error") String error);
}
