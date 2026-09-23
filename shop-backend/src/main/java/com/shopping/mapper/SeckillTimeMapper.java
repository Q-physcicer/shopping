package com.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.pojo.SeckillTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeckillTimeMapper extends BaseMapper<SeckillTime> {

        @Select("select * from seckill_time where end_time > #{time} limit 6")
        List<SeckillTime> getTime(long time);

        @Delete("delete from seckill_time")
        void deleteAll();

        @Select("select end_time from seckill_time where time_id = #{timeId}")
        Long getEndTime(Integer timeId);
}
