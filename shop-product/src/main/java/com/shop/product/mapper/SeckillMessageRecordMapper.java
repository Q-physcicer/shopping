package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.product.pojo.SeckillMessageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 秒杀消息记录（product 端）。
 * 消费侧的 CAS 幂等更新在 order 服务的同名表副本中实现；本接口只做生产端状态机：
 * 写入(SENT) → MQ 确认失败(FAILED)。result 查询接口读 status 供前端轮询。
 */
@Mapper
public interface SeckillMessageRecordMapper extends BaseMapper<SeckillMessageRecord> {

    @Select("SELECT * FROM seckill_message_record WHERE message_id = #{messageId} LIMIT 1")
    SeckillMessageRecord findByMessageId(@Param("messageId") String messageId);

    @Update("UPDATE seckill_message_record SET `status` = 'FAILED', " +
            "error_message = #{error}, updated_at = NOW() " +
            "WHERE message_id = #{messageId} AND `status` = 'SENT'")
    int markFailedIfSent(@Param("messageId") String messageId, @Param("error") String error);
}