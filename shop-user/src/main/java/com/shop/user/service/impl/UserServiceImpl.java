package com.shop.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.user.mapper.UserMapper;
import com.shop.user.pojo.User;
import com.shop.user.service.UserService;
import com.shop.common.util.MD5Util;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务。密码策略（P1）：
 * - 新注册：BCrypt 加密落库
 * - 登录：库中为 BCrypt 格式（$2 开头）用 BCrypt 比对；
 *   否则按旧 MD5 逻辑比对，成功后透明升级为 BCrypt 落库（存量用户无感迁移）
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    /** BCrypt 密文固定以 $2a/$2b/$2y 开头，长度 60；据此区分存量 MD5（32位十六进制） */
    private static boolean isBcrypt(String password) {
        return password != null && password.length() >= 59 && password.startsWith("$2");
    }

    public User getUserById(Long id) {
        return getById(id);
    }

    public boolean saveUser(User user) {
        return save(user);
    }

    /**
     * 登录校验（兼容存量 MD5 + BCrypt 透明升级）
     * @param user 前端提交的 username + 明文 password
     */
    public User login(User user) {
        User one = lambdaQuery().eq(User::getUsername, user.getUsername()).one();
        if (one == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        String rawPassword = user.getPassword();
        String stored = one.getPassword();
        if (isBcrypt(stored)) {
            if (!BCRYPT.matches(rawPassword, stored)) {
                throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
            }
        } else {
            // 旧 MD5：MD5(明文) 直接比对
            if (!stored.equalsIgnoreCase(MD5Util.MD5Encode(rawPassword + "", "UTF-8"))) {
                throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
            }
            // 透明升级为 BCrypt
            one.setPassword(BCRYPT.encode(rawPassword));
            updateById(one);
        }
        return one;
    }

    public void register(User user) {
        if (lambdaQuery().eq(User::getUsername, user.getUsername()).exists()) {
            throw new XmException(ExceptionEnum.SAVE_USER_REUSE);
        }
        // BCrypt 加密落库
        user.setPassword(BCRYPT.encode(user.getPassword()));
        user.setRole("USER");   // 注册用户默认 USER 角色
        try {
            save(user);
        } catch (Exception e) {
            e.printStackTrace();
            throw new XmException(ExceptionEnum.SAVE_USER_ERROR);
        }
    }

    public void isUserName(String username) {
        if (lambdaQuery().eq(User::getUsername, username).count() == 1) {
            throw new XmException(ExceptionEnum.SAVE_USER_REUSE);
        }
    }

    /**
     * 修改密码（个人中心）：校验旧密码（兼容历史 MD5），更新为 BCrypt。
     */
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null || oldPassword == null) {
            return false;
        }
        String stored = user.getPassword();
        boolean oldOk;
        if (isBcrypt(stored)) {
            oldOk = BCRYPT.matches(oldPassword, stored);
        } else {
            oldOk = stored.equalsIgnoreCase(MD5Util.MD5Encode(oldPassword + "", "UTF-8"));
        }
        if (!oldOk) {
            return false;
        }
        user.setPassword(BCRYPT.encode(newPassword));
        return updateById(user);
    }
}