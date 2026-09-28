package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.product.pojo.SeckillTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper   // 原 @Repository 注解不会被 MyBatis-Plus 自动扫描，导致无 bean；迁移时修正
public interface SeckillTimeMapper extends BaseMapper<SeckillTime> {

        @Select("select * from seckill_time where end_time > #{time} limit 6")
        List<SeckillTime> getTime(long time);

        @Delete("delete from seckill_time")
        void deleteAll();

        @Select("select end_time from seckill_time where time_id = #{timeId}")
        Long getEndTime(Integer timeId);
}
