package com.shop.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.util.Result;
import com.shop.user.mapper.UserMapper;
import com.shop.user.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * user 内部接口（P5）：注册时间戳靠 V3 的 created_at 列，存量用户统一为迁移时刻。
 * 网关已对 /user/internal/** 403；此为第二道闸（业务端口暴露时的纵深防御）。
 */
@RestController
@RequestMapping("/user/internal")
public class UserInternalController {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 统一 ADMIN 硬校验 */
    private Result requireAdmin() {
        com.shop.common.context.UserContext.Principal p = com.shop.common.context.UserContext.get();
        if (p == null || !p.isAdmin()) {
            return Result.fail("无权访问内部接口（需管理员身份）", null);
        }
        return null;
    }

    /** 总用户数 */
    @GetMapping("/stats/count")
    public Result count() {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        Long n = userMapper.selectCount(new LambdaQueryWrapper<>());
        Map<String, Object> m = new HashMap<>();
        m.put("total", n);
        m.put("admins", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user WHERE role='ADMIN'", Long.class));
        return Result.success("success", m);
    }

    /** 近 N 日每日新增注册曲线 */
    @GetMapping("/stats/growth")
    public Result growth(@RequestParam(value = "days", defaultValue = "7") int days) {
        Result denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        if (days < 1 || days > 90) {
            days = 7;
        }
        List<Map<String, Object>> daily = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS newUsers " +
                        "FROM user WHERE created_at > DATE_SUB(NOW(), INTERVAL ? DAY) " +
                        "GROUP BY day ORDER BY day", days);
        return Result.success("success", daily);
    }
}