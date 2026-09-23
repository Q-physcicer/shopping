package com.shopping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shopping.pojo.User;
import com.shopping.vo.CartVo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserService extends IService <User> {

    public User getUserById(Long id);

    public boolean saveUser(User user);




}


