package com.shop.user.controller;

import com.shop.common.context.UserContext;
import com.shop.common.util.CookieUtil;
import com.shop.common.util.JwtUtil;
import com.shop.common.util.Result;
import com.shop.user.pojo.User;
import com.shop.user.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户接口（P1 JWT 改造）：
 * - 登录成功签发 JWT（HS256, 7天），Cookie 名沿用 XM_TOKEN
 * - /user/token 从网关注入的 X-User-Id 查库返回用户信息（不再依赖 Redis 会话 Hash）
 * - 登出：jti 写入 Redis 黑名单，网关过滤器统一校验
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserServiceImpl u;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/login")
    public Result login(@RequestBody User user, HttpServletRequest request, HttpServletResponse response) {
        User dbUser = u.login(user);
        // 签发 JWT（含角色，供网关鉴权与管理端判断）
        String role = dbUser.getRole() == null ? "USER" : dbUser.getRole();
        String jwt = jwtUtil.generate(dbUser.getUserId().longValue(), dbUser.getUsername(), role);
        // Cookie 名保持 XM_TOKEN 不变：前端路由守卫与 EventSource 免改造
        CookieUtil.setCookie(request, response, "XM_TOKEN", jwt, 7 * 24 * 3600);
        dbUser.setPassword(null);
        return Result.success("登录成功", dbUser);
    }

    /** 用户注册 */
    @PostMapping("/register")
    public Result register(@RequestBody User user) {
        u.register(user);
        return Result.success("注册成功");
    }

    /** 判断用户名是否已存在 */
    @GetMapping("/username/{username}")
    public Result username(@PathVariable String username) {
        u.isUserName(username);
        return Result.success("可注册");
    }

    /**
     * 获取当前登录用户信息（网关已注入 X-User-Id）。
     * 兼容期：老天桥路由路由守卫调用此接口判断登录态。
     */
    @GetMapping("/token")
    public Result token(HttpServletRequest request, HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 未带 JWT（可能以匿名身份通过白名单）——视为未登录
            CookieUtil.delCookie(request, response, "XM_TOKEN");
            return Result.fail("账号过期,请重新登录", null);
        }
        User user = u.getUserById(userId);
        if (user == null) {
            CookieUtil.delCookie(request, response, "XM_TOKEN");
            return Result.fail("账号不存在", null);
        }
        user.setPassword(null);
        return Result.success("success", user);
    }

    /**
     * 修改账户资料（个人中心）：当前仅支持手机号。
     */
    @PutMapping("/me")
    public Result updateMe(@RequestBody User body) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.fail("请先登录", null);
        }
        User dbUser = u.getUserById(userId);
        if (dbUser == null) {
            return Result.fail("账号不存在", null);
        }
        // 逐字段拷贝，绝不信任 body 里的 userId/role/password
        if (body.getUserPhoneNumber() != null) {
            dbUser.setUserPhoneNumber(body.getUserPhoneNumber());
        }
        u.saveUser(dbUser);
        return Result.success("资料已更新");
    }

    /**
     * 修改密码（个人中心）：校验旧密码后 BCrypt 更新。
     */
    @PutMapping("/password")
    public Result updatePassword(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.fail("请先登录", null);
        }
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return Result.fail("新密码至少 6 位", null);
        }
        boolean ok = u.updatePassword(userId, oldPassword, newPassword);
        return ok ? Result.success("密码已修改") : Result.fail("旧密码不正确", null);
    }

    /**
     * 登出：把 JWT 的 jti 加入黑名单（TTL=JWT 剩余有效期），网关侧统一拦截。
     */
    @PostMapping("/logout")
    public Result logout(@CookieValue(value = "XM_TOKEN", required = false) String token,
                         HttpServletRequest request, HttpServletResponse response) {
        if (token != null && !token.isBlank()) {
            try {
                String jti = jwtUtil.parse(token).getId();
                stringRedisTemplate.opsForValue().set("auth:logout:" + jti, "1", 7, TimeUnit.DAYS);
            } catch (Exception ignored) {
                // 无效 token 直接清 cookie 即可
            }
        }
        CookieUtil.delCookie(request, response, "XM_TOKEN");
        return Result.success("退出成功");
    }
}