package com.shopping.Controller;

import com.shopping.pojo.User;
import com.shopping.service.impl.UserServiceImpl;
import com.shopping.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserServiceImpl u;
    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/index")
    public User index() {
        System.out.println("index");
        return u.getUserById(1L);
    }

    @PostMapping("/login")
    public Result login(@RequestBody User user, HttpServletRequest request, HttpServletResponse response) {
        user = u.login(user);
        // 添加cookie，设置唯一认证
        String encode = MD5Util.MD5Encode(user.getUsername() + user.getPassword(), "UTF-8");
        // 进行加盐
        encode += "|" + user.getUserId() + "|" + user.getUsername() + "|";
        CookieUtil.setCookie(request, response, "XM_TOKEN", encode, 1800);
        // 将encode放入redis中，用于认证，还未开启时就不要打开
        try {
            redisTemplate.opsForHash().putAll(encode, BeanUtil.bean2map(user));
            redisTemplate.expire(encode, 30 * 60, TimeUnit.SECONDS); // 设置过期时间
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将密码设为null,返回给前端
        user.setPassword(null);
        return Result.success("登录成功", user);
    }

    /**
     * 用户注册
     * @param user
     * @return
     */
    @PostMapping("/register") // 用户注册
    public Result register(@RequestBody User user) {
        u.register(user);
        return Result.success("注册成功");
    }

    /**
     * 判断用户名是否已存在
     * @param username
     * @return
     */
    @GetMapping("/username/{username}") //  判断用户名是否已存在
    public Result username(@PathVariable String username) {
        u.isUserName(username);
        return Result.success("可注册");
    }

    /**
     * 根据token获取用户信息
     * @param token
     * @return
     */
    @GetMapping("/token") //  根据token获取用户信息
    public Result token(@CookieValue("XM_TOKEN") String token, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map map = redisTemplate.opsForHash().entries(token);
        // 可能map为空 ， 即redis中时间已过期，但是cookie还存在。
        // 这个时候应该删除cookie，让用户重新登录
        if (map.isEmpty()) {
            CookieUtil.delCookie(request, token);
            return Result.fail("账号过期,请重新登录",null);
        }

        redisTemplate.expire(token, 30 * 60, TimeUnit.SECONDS); // 设置过期时间
        User user = BeanUtil.map2bean(map, User.class);
        user.setPassword(null);
        return Result.success("success",user);
    }
}
