package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.user.mapper.UserAddressMapper;
import com.shop.user.pojo.UserAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务（个人中心）。设默认时先清同用户其它默认地址，保证单一默认。
 */
@Service
public class UserAddressServiceImpl {

    @Autowired
    private UserAddressMapper addressMapper;

    /** 我的地址列表（默认地址在前，最近更新在前） */
    public List<UserAddress> listByUser(Integer userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdatedTime)
                .orderByDesc(UserAddress::getId));
    }

    /** 新增地址；首条自动设为默认 */
    @Transactional
    public UserAddress save(Integer userId, UserAddress address) {
        long now = System.currentTimeMillis();
        long count = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
        boolean wantDefault = address.getIsDefault() != null && address.getIsDefault() == 1;
        address.setId(null);
        address.setUserId(userId);
        address.setIsDefault((count == 0 || wantDefault) ? 1 : 0);
        address.setCreatedTime(now);
        address.setUpdatedTime(now);
        if (address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        addressMapper.insert(address);
        return address;
    }

    /** 修改地址（仅允许改本人地址） */
    @Transactional
    public boolean update(Integer userId, Long id, UserAddress address) {
        UserAddress exist = getOwned(userId, id);
        if (exist == null) {
            return false;
        }
        address.setId(id);
        address.setUserId(userId);
        address.setUpdatedTime(System.currentTimeMillis());
        // isDefault 只接受 0/1（原缺陷：客户端可写任意整数入库）
        address.setIsDefault(address.getIsDefault() != null && address.getIsDefault() == 1 ? 1 : 0);
        if (address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        return addressMapper.updateById(address) > 0;
    }

    /** 设为默认地址 */
    @Transactional
    public boolean setDefault(Integer userId, Long id) {
        UserAddress exist = getOwned(userId, id);
        if (exist == null) {
            return false;
        }
        clearDefault(userId);
        exist.setIsDefault(1);
        exist.setUpdatedTime(System.currentTimeMillis());
        return addressMapper.updateById(exist) > 0;
    }

    /** 删除地址（仅允许删本人地址） */
    public boolean delete(Integer userId, Long id) {
        UserAddress exist = getOwned(userId, id);
        if (exist == null) {
            return false;
        }
        return addressMapper.deleteById(id) > 0;
    }

    /** 当前用户的默认地址（无则 null） */
    public UserAddress getDefault(Integer userId) {
        return addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .last("LIMIT 1"));
    }

    /** 查本人地址（controller 给 Feign 端点复用）；非本人或不存在返回 null */
    public UserAddress getOwned(Integer userId, Long id) {
        return addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId));
    }

    /**
     * 清默认地址改单条原子 UPDATE（P2 原缺陷：select-then-update 两步在并发设默认时
     * 可产生双默认；原子清保证最后的 setDefault 落库即唯一）。
     */
    private void clearDefault(Integer userId) {
        UserAddress clr = new UserAddress();
        clr.setIsDefault(0);
        addressMapper.update(clr, new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1));
    }
}
