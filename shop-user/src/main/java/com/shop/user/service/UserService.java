package com.shop.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.user.pojo.User;
import com.shop.user.vo.CartVo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserService extends IService <User> {

    public User getUserById(Long id);

    public boolean saveUser(User user);




}


