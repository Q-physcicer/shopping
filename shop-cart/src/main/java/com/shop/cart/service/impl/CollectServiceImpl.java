package com.shop.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.cart.mapper.CollectMapper;
import com.shop.cart.pojo.Collect;
import com.shop.cart.pojo.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * @Auther: wdd
 * @Date: 2020-03-19 13:21
 * @Description: 收藏服务实现类
 */
@Service
public class CollectServiceImpl {

    @Autowired
    private CollectMapper collectMapper;

    @Transactional
    public void addCollect(String userId, String productId) {
        // 检查是否已收藏
        LambdaQueryWrapper<Collect> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Collect::getUserId, Integer.parseInt(userId))
                .eq(Collect::getProductId, Integer.parseInt(productId));

        if (collectMapper.selectCount(queryWrapper) > 0) {
            throw new XmException(ExceptionEnum.SAVE_COLLECT_REUSE);
        }

        // 新增收藏记录
        Collect collect = new Collect();
        collect.setUserId(Integer.parseInt(userId));
        collect.setProductId(Integer.parseInt(productId));
        collect.setCollectTime(new Date().getTime());

        int count = collectMapper.insert(collect);
        if (count != 1) {
            throw new XmException(ExceptionEnum.SAVE_COLLECT_ERROR);
        }
    }

    /** 收藏列表（空集合正常返回——P8 同款修复：原抛 GET_COLLECT_NOT_FOUND 让前端/Agent 把"无收藏"当查询失败） */
    public List<Product> getCollect(String userId) {
        return collectMapper.getCollect(userId);
    }

    public void deleteCollect(String userId, String productId) {
        // 构造删除条件
        LambdaQueryWrapper<Collect> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Collect::getUserId, Integer.parseInt(userId))
                .eq(Collect::getProductId, Integer.parseInt(productId));

        int count = collectMapper.delete(queryWrapper);
        if (count != 1) {
            throw new XmException(ExceptionEnum.DELETE_COLLECT_ERROR);
        }
    }
}
