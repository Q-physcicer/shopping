package com.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.pojo.SeckillMessageRecord;
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
}
