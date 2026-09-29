package com.shop.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.admin.mapper.CarouselMapper;
import com.shop.admin.mapper.CategoryMapper;
import com.shop.admin.mapper.SeckillTimeMapper;
import com.shop.admin.mapper.UserMapper;
import com.shop.admin.pojo.Carousel;
import com.shop.admin.pojo.Category;
import com.shop.admin.pojo.SeckillTime;
import com.shop.admin.pojo.User;
import com.shop.common.util.Result;
import com.shop.feign.OrderClient;
import com.shop.feign.ProductClient;
import com.shop.feign.UserClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端其余聚合接口（P5）：分类 / 轮播 / 秒杀 / 用户 / 订单（Feign 转发） / 统计 / ES 重建。
 */
@RestController
@RequestMapping("/admin")
public class AdminOpsController {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private CarouselMapper carouselMapper;
    @Autowired
    private SeckillTimeMapper seckillTimeMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private OrderClient orderClient;
    @Autowired
    private UserClient userClient;
    @Autowired
    private ProductClient productClient;

    // ---------------- 分类 ----------------

    @GetMapping("/category/list")
    public Result categories() {
        return Result.success("success", categoryMapper.selectList(new LambdaQueryWrapper<>()));
    }

    // ---------------- 轮播 ----------------

    @PostMapping("/carousel")
    public Result createCarousel(@RequestBody Carousel carousel) {
        if (carousel.getImgPath() == null) {
            return Result.fail("图片路径必填", null);
        }
        carousel.setCarouselId(null);
        carouselMapper.insert(carousel);
        return Result.success("新增成功", carousel);
    }

    @DeleteMapping("/carousel/{carouselId}")
    public Result deleteCarousel(@PathVariable Integer carouselId) {
        carouselMapper.deleteById(carouselId);
        return Result.success("删除成功");
    }

    // ---------------- 秒杀管理 ----------------

    /** 秒杀场次列表 */
    @GetMapping("/seckill/time")
    public Result seckillTimes() {
        return Result.success("success", seckillTimeMapper.selectList(
                new LambdaQueryWrapper<SeckillTime>().orderByAsc(SeckillTime::getStartTime)));
    }

    /**
     * 新增秒杀商品（P1 正轨化）：Feign 调 product 侧 /seckill/admin/add。
     * product 端负责写库 + 校验（价格≤售价/库存≤商品库存）+ 失效列表缓存 + manual 场标记
     * （原实现在此直插 product 域表且不清缓存，新加商品对购物端不可见；手动场还会被每日 15:00 重建清掉）。
     */
    @PostMapping("/seckill")
    public Result addSeckill(@RequestBody Map<String, Object> body) {
        return productClient.addSeckill(body);
    }

    // ---------------- 用户管理 ----------------

    @GetMapping("/user/page")
    public Result userPage(@RequestParam(value = "page", defaultValue = "1") int page,
                           @RequestParam(value = "size", defaultValue = "20") int size,
                           @RequestParam(value = "keyword", required = false) String keyword) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(page, 1);
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(User::getUsername, keyword);
        }
        Long total = userMapper.selectCount(w);
        w.orderByDesc(User::getUserId);
        w.last("LIMIT " + (Math.max(page - 1, 0)) * size + ", " + size);
        List<User> rows = userMapper.selectList(w);
        rows.forEach(u -> u.setPassword(null));   // 密码不外发
        return Result.success("success", Map.of("total", total, "list", rows));
    }

    /**
     * 角色 USER<->ADMIN 切换（P0-8 加防护）：
     * - 不能改自己（防唯一管理员自降级把自己锁死在管理端外）
     * - ADMIN 降级时若仅剩该一个 ADMIN 则拒绝（保底至少一名管理员）
     * 注意：网关只验 JWT 内 role 不查库，被降级者的旧 token 在有效期内角色不变（幽灵管理员窗口），
     * 详见 deploy/README.md 安全线说明。
     */
    @PostMapping("/user/{userId}/role")
    public Result toggleRole(@PathVariable Integer userId) {
        Long operatorId = com.shop.common.context.UserContext.getUserId();
        if (operatorId != null && operatorId.intValue() == userId) {
            return Result.fail("不能修改自己的角色", null);
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return Result.fail("用户不存在", null);
        }
        boolean toAdmin = !"ADMIN".equals(u.getRole());
        if (!toAdmin) {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, "ADMIN"));
            if (adminCount <= 1) {
                return Result.fail("系统至少保留一名管理员", null);
            }
        }
        u.setRole(toAdmin ? "ADMIN" : "USER");
        userMapper.updateById(u);
        return Result.success("角色已切换为 " + u.getRole());
    }

    // ---------------- 订单管理（Feign 转发到 order） ----------------

    @GetMapping("/order/page")
    public Result orderPage(@RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "size", defaultValue = "20") int size,
                            @RequestParam(value = "status", required = false) Integer status,
                            @RequestParam(value = "orderId", required = false) String orderId) {
        return orderClient.pageOrders(page, size, status, orderId);
    }

    /** 管理端标记订单完成（Feign 转发到 order，CAS 已支付→已完成） */
    @PostMapping("/order/done")
    public Result markOrderDone(@RequestBody Map<String, Object> body) {
        return orderClient.markOrderDone(body);
    }

    // ---------------- 售后管理（P8，Feign 转发到 order；网关 /admin/** 已校验 ADMIN） ----------------

    @GetMapping("/aftersale/page")
    public Result aftersalePage(@RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "size", defaultValue = "20") int size,
                                @RequestParam(value = "status", required = false) Integer status) {
        return orderClient.pageAftersales(page, size, status);
    }

    /** 售后审批：body {aftersaleId, action: approve|reject, reason} */
    @PostMapping("/aftersale/handle")
    public Result aftersaleHandle(@RequestBody Map<String, Object> body) {
        Object action = body.get("action");
        if (action == null || (!"approve".equals(action) && !"reject".equals(action))) {
            return Result.fail("action 必须为 approve 或 reject", null);
        }
        if ("reject".equals(action) && (body.get("reason") == null || String.valueOf(body.get("reason")).isBlank())) {
            return Result.fail("拒绝售后必须填写理由", null);
        }
        return orderClient.handleAftersale(body);
    }

    // ---------------- 统计聚合 ----------------

    /**
     * Dashboard overview：Feign 聚合 order(GMV/订单曲线) + product(销量Top/低库存) + user(总数/增长曲线)。
     */
    @GetMapping("/stats/overview")
    public Result overview(@RequestParam(value = "days", defaultValue = "7") int days) {
        Map<String, Object> data = new java.util.HashMap<>();
        try {
            data.put("order", orderClient.gmv(days).getData());
        } catch (Exception e) {
            data.put("order", Map.of("error", "order 服务获取失败: " + e.getMessage()));
        }
        try {
            data.put("topSales", productClient.topSales(10).getData());
        } catch (Exception e) {
            data.put("topSales", List.of());
        }
        try {
            data.put("user", userClient.userCount().getData());
        } catch (Exception e) {
            data.put("user", Map.of());
        }
        try {
            data.put("userGrowth", userClient.growth(days).getData());
        } catch (Exception e) {
            data.put("userGrowth", List.of());
        }
        return Result.success("success", data);
    }

    // ---------------- 系统设置 ----------------

    /** ES 全量重建（Feign 到 product） */
    @PostMapping("/es/rebuild")
    public Result rebuildEs() {
        return productClient.rebuildEs();
    }
}