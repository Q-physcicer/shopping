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
        product.setProductId(null);
        // 商品标题 NOT NULL：未传时用名称兜底（超长截断到列宽 30）
        if (product.getProductTitle() == null || product.getProductTitle().isBlank()) {
            product.setProductTitle(product.getProductName().length() > 28
                    ? product.getProductName().substring(0, 28) : product.getProductName());
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

    /** 改价 / 改库存（部分更新） */
    @PostMapping("/{productId}/update")
    public Result update(@PathVariable Integer productId, @RequestBody Map<String, Object> body) {
        Product exists = productMapper.selectById(productId);
        if (exists == null) {
            return Result.fail("商品不存在", null);
        }
        if (body.containsKey("productPrice")) {
            exists.setProductPrice(Double.valueOf(String.valueOf(body.get("productPrice"))));
        }
        if (body.containsKey("productSellingPrice")) {
            exists.setProductSellingPrice(Double.valueOf(String.valueOf(body.get("productSellingPrice"))));
        }
        if (body.containsKey("productNum")) {
            exists.setProductNum(Integer.valueOf(String.valueOf(body.get("productNum"))));
        }
        if (body.containsKey("productSales")) {
            exists.setProductSales(Integer.valueOf(String.valueOf(body.get("productSales"))));
        }
        if (body.containsKey("productTitle")) {
            exists.setProductTitle(String.valueOf(body.get("productTitle")));
        }
        if (body.containsKey("productIntro")) {
            exists.setProductIntro(String.valueOf(body.get("productIntro")));
        }
        productMapper.updateById(exists);
        publishChange(productId);
        return Result.success("更新成功");
    }

    /** 下架（软下架：库存置 0，ES 消费端会把商品移出索引） */
    @DeleteMapping("/{productId}")
    public Result offline(@PathVariable Integer productId) {
        Product p = productMapper.selectById(productId);
        if (p == null) {
            return Result.fail("商品不存在", null);
        }
        p.setProductNum(0);
        productMapper.updateById(p);
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