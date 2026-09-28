package com.shop.product.controller;

import com.shop.common.util.Result;
import com.shop.product.service.impl.SearchServiceImpl;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索接口（P3，公开只读，网关 GET 白名单放行）：
 * GET  /product/search?keyword=&page=&size=&categoryId=&sort=sales|price
 * POST /product/internal/es/rebuild —— 全量重建索引（P5 管理端经 Feign 调用；网关层 /admin/** 鉴权保护）
 */
@RestController
public class SearchController {

    @Autowired
    private SearchServiceImpl searchService;
    @Autowired
    private com.shop.product.es.ProductEsService productEsService;

    /** 应用就绪后自动初始化索引（幂等；ES 未启用时为 no-op） */
    @PostConstruct
    public void init() {
        try {
            productEsService.initIndexIfAbsent();
        } catch (Exception ignored) {
            // initIndexIfAbsent 内部已兜底，这里再防御一层避免影响 bean 创建
        }
    }

    @GetMapping("/product/search")
    public Result search(@RequestParam(value = "keyword", required = false) String keyword,
                         @RequestParam(value = "categoryId", required = false) Integer categoryId,
                         @RequestParam(value = "sort", required = false, defaultValue = "relevance") String sort,
                         @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                         @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        if (keyword == null || keyword.isBlank()) {
            return Result.fail("搜索关键词不能为空", null);
        }
        return Result.success("success", searchService.search(keyword.trim(), categoryId, sort, page, size));
    }

    @PostMapping("/product/internal/es/rebuild")
    public Result rebuild() {
        try {
            long n = searchService.rebuild();
            return Result.success("索引重建成功，共 " + n + " 条");
        } catch (Exception e) {
            return Result.fail("索引重建失败：" + e.getMessage(), null);
        }
    }
}