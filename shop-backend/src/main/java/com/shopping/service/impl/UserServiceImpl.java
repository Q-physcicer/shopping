package com.shopping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.UserMapper;
import com.shopping.pojo.User;
import com.shopping.service.UserService;
import com.shopping.util.MD5Util;
import org.springframework.stereotype.Service;


@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    public User getUserById(Long id) {
        return getById(id);
    }

    public boolean saveUser(User user) {
        return save(user);
    }

    public User login(User user) {
        user.setPassword(MD5Util.MD5Encode(user.getPassword() + "", "UTF-8"));
        // 根据用户名和密码查询用户
        User one = lambdaQuery().eq(User::getUsername, user.getUsername()).eq(User::getPassword, user.getPassword()).one();
        if (one == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return one;
    }

    public void register(User user) {
        if (lambdaQuery().eq(User::getUsername, user.getUsername()).exists()) {
            throw new XmException(ExceptionEnum.SAVE_USER_REUSE);
        }

        // 使用md5对密码进行加密
        user.setPassword(MD5Util.MD5Encode(user.getPassword() + "", "UTF-8"));
        // 存入数据库
        try {
            save(user);
        } catch (Exception e) {
            e.printStackTrace();
            throw new XmException(ExceptionEnum.SAVE_USER_ERROR);
        }
    }

    public void isUserName(String username) {
        // 先去看看用户名是否重复
        if (lambdaQuery().eq(User::getUsername, username).count() == 1) {
            // 用户名已存在
            throw new XmException(ExceptionEnum.SAVE_USER_REUSE);
        }
    }
}
