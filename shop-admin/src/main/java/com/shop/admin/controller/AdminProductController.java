package com.shop.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.admin.mapper.ProductMapper;
import com.shop.common.util.Result;
import com.shop.admin.pojo.Product;
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
 * 商品管理（P5）：列表/上架/改价/改库存/下架。
 * 表归属 product 域，此处属"管理端聚合写"（单库共享 + 跨域写经审计的事件补偿）：
 * 每次变更投递 shop.product.change 事件，product 服务消费同步 ES。
 * 路径 /admin/** 由网关校验 ADMIN 角色。
 */
@RestController
@RequestMapping("/admin/product")
public class AdminProductController {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /** 商品分页列表（关键词/分类过滤） */
    @GetMapping("/page")
    public Result page(@RequestParam(value = "page", defaultValue = "1") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "categoryId", required = false) Integer categoryId) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(page, 1);
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(Product::getProductName, keyword);
        }
        if (categoryId != null) {
            w.eq(Product::getCategoryId, categoryId);
        }
        Long total = productMapper.selectCount(w);
        w.orderByDesc(Product::getProductId);
        w.last("LIMIT " + (Math.max(page - 1, 0)) * size + ", " + size);
        List<Product> rows = productMapper.selectList(w);
        return Result.success("success", Map.of("total", total, "list", rows));
    }

    /** 上架新商品 */
    @PostMapping("")
    public Result create(@RequestBody Product product) {
        if (product.getProductName() == null || product.getCategoryId() == null) {
            return Result.fail("商品名称与分类必填", null);
        }
        if (product.getProductPrice() != null && product.getProductPrice() <= 0) {
            return Result.fail("原价必须大于 0", null);
        }
        if (product.getProductNum() != null && product.getProductNum() < 0) {
            return Result.fail("库存不能为负数", null);
        }
        product.setProductId(null);
        // 商品标题 NOT NULL：未传时用名称兜底（V4 列宽 60）
        if (product.getProductTitle() == null || product.getProductTitle().isBlank()) {
            product.setProductTitle(truncate(product.getProductName(), 60));
        }
        if (product.getProductSales() == null) {
            product.setProductSales(0);
        }
        if (product.getVersion() == null) {
            product.setVersion(1);
        }
        productMapper.insert(product);
        publishChange(product.getProductId());
        return Result.success("上架成功", product);
    }

    /** 改商品（部分更新；白名单补全 + 数值校验 + 乐观锁返回值检查） */
    @PostMapping("/{productId}/update")
    public Result update(@PathVariable Integer productId, @RequestBody Map<String, Object> body) {
        Product exists = productMapper.selectById(productId);
        if (exists == null) {
            return Result.fail("商品不存在", null);
        }
        try {
            if (body.containsKey("productName")) {
                String name = String.valueOf(body.get("productName"));
                if (name.isBlank()) {
                    return Result.fail("商品名不能为空", null);
                }
                exists.setProductName(truncate(name, 100));
            }
            if (body.containsKey("categoryId")) {
                Integer cid = Integer.valueOf(String.valueOf(body.get("categoryId")));
                if (cid <= 0) {
                    return Result.fail("分类不合法", null);
                }
                exists.setCategoryId(cid);
            }
            if (body.containsKey("productPicture")) {
                exists.setProductPicture(truncate(String.valueOf(body.get("productPicture")), 200));
            }
            if (body.containsKey("productPrice")) {
                double v = Double.parseDouble(String.valueOf(body.get("productPrice")));
                if (v <= 0) {
                    return Result.fail("原价必须大于 0", null);
                }
                exists.setProductPrice(v);
            }
            if (body.containsKey("productSellingPrice")) {
                double v = Double.parseDouble(String.valueOf(body.get("productSellingPrice")));
                if (v <= 0) {
                    return Result.fail("售价必须大于 0", null);
                }
                exists.setProductSellingPrice(v);
            }
            if (body.containsKey("productNum")) {
                int v = Integer.parseInt(String.valueOf(body.get("productNum")));
                if (v < 0) {
                    return Result.fail("库存不能为负数", null);
                }
                exists.setProductNum(v);
            }
            if (body.containsKey("productSales")) {
                int v = Integer.parseInt(String.valueOf(body.get("productSales")));
                if (v < 0) {
                    return Result.fail("销量不能为负数", null);
                }
                exists.setProductSales(v);
            }
            if (body.containsKey("productTitle")) {
                // V4 已加宽到 60；两处截断口径统一
                exists.setProductTitle(truncate(String.valueOf(body.get("productTitle")), 60));
            }
            if (body.containsKey("productIntro")) {
                exists.setProductIntro(truncate(String.valueOf(body.get("productIntro")), 500));
            }
        } catch (NumberFormatException e) {
            return Result.fail("参数格式错误（价格/库存/销量必须为数字）", null);
        }
        int updated = productMapper.updateById(exists);
        if (updated == 0) {
            // 乐观锁冲突（原实现静默报成功，并发改同一商品一方改动被吞）
            return Result.fail("并发冲突，请刷新后重试", null);
        }
        publishChange(productId);
        return Result.success("更新成功");
    }

    /** 字符串安全截断（数据库列宽保护） */
    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 下架（软下架：库存置 0，ES 消费端会把商品移出索引） */
    @DeleteMapping("/{productId}")
    public Result offline(@PathVariable Integer productId) {
        Product p = productMapper.selectById(productId);
        if (p == null) {
            return Result.fail("商品不存在", null);
        }
        p.setProductNum(0);
        if (productMapper.updateById(p) == 0) {
            return Result.fail("并发冲突，请刷新后重试", null);
        }
        publishChange(productId);
        return Result.success("已下架（库存置0并移出搜索）");
    }

    /** 投递商品变更事件（product 服务消费同步 ES） */
    private void publishChange(Integer productId) {
        try {
            rabbitTemplate.convertAndSend("shop.product.change", "product.update",
                    String.valueOf(productId));
        } catch (Exception ignored) {
            // ES 同步失败不阻塞管理操作；重建索引按钮可兜底
        }
    }
}