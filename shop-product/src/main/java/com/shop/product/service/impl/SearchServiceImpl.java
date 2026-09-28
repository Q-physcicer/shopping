package com.shop.product.service.impl;

import com.shop.product.es.ProductEsService;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.pojo.Product;
import com.shop.product.vo.SearchProductVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索门面（P3）：ES 优先、MySQL 兜底。
 * 熔断策略：ElasticsearchTemplate 抛出任何异常 → 置熔断标记（到下次重建前不再打 ES）→ 降级 MySQL LIKE。
 * 这样服务器暂未装好 ES / ES 抖动都不会影响前端搜索可用性。
 */
@Service
@Slf4j
public class SearchServiceImpl {

    /** ES 连续失败后的熔断标记（简单内存版：单实例场景够用；异常时置位，重建接口成功后复位） */
    private volatile boolean esCircuitOpen = false;

    @Autowired
    private ProductEsService productEsService;
    @Autowired
    private ProductMapper productMapper;

    public Map<String, Object> search(String keyword, Integer categoryId, String sort, int page, int size) {
        boolean useEs = productEsService.isEsEnabled() && !esCircuitOpen;
        if (useEs) {
            try {
                SearchHits<com.shop.product.es.ProductDoc> hits =
                        productEsService.search(keyword, categoryId, sort, page, size);
                List<SearchProductVo> list = hits.getSearchHits().stream().map(this::hitToVo).toList();
                return Map.of(
                        "total", hits.getTotalHits(),
                        "page", page,
                        "size", size,
                        "engine", "elasticsearch",
                        "list", list);
            } catch (Exception e) {
                esCircuitOpen = true;
                log.warn("[Search] ES 查询失败，已熔断并降级 MySQL：{}", e.getMessage());
            }
        }
        return mysqlFallback(keyword, categoryId, sort, page, size);
    }

    /** ES 重建（管理端按钮）——成功后解除熔断 */
    public long rebuild() {
        long n;
        if (!productEsService.isEsEnabled()) {
            throw new IllegalStateException("ES 未开启（shop.search.es-enabled=false）");
        }
        n = productEsService.rebuildIndex();
        esCircuitOpen = false;
        return n;
    }

    // ---------------- MySQL 降级实现 ----------------

    private Map<String, Object> mysqlFallback(String keyword, Integer categoryId, String sort, int page, int size) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .and(w -> w.like(Product::getProductName, keyword)
                                .or().like(Product::getProductTitle, keyword)
                                .or().like(Product::getProductIntro, keyword));
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if ("sales".equals(sort)) {
            wrapper.orderByDesc(Product::getProductSales);
        } else if ("price".equals(sort)) {
            wrapper.orderByAsc(Product::getProductSellingPrice);
        }
        long total = productMapper.selectCount(wrapper);
        wrapper.last("LIMIT " + (Math.max(page - 1, 0)) * size + ", " + size);
        List<Product> products = productMapper.selectList(wrapper);

        List<SearchProductVo> list = new ArrayList<>();
        for (Product p : products) {
            SearchProductVo vo = new SearchProductVo();
            vo.setProductId(p.getProductId());
            // 简易高亮（与 ES 的 <em> 标记一致，前端渲染逻辑统一）
            vo.setProductName(highlight(p.getProductName(), keyword));
            vo.setProductTitle(highlight(p.getProductTitle(), keyword));
            vo.setProductIntro(highlight(introSnippet(p.getProductIntro(), keyword), keyword));
            vo.setProductPicture(p.getProductPicture());
            vo.setProductPrice(p.getProductPrice());
            vo.setProductSellingPrice(p.getProductSellingPrice());
            vo.setProductSales(p.getProductSales());
            vo.setCategoryId(p.getCategoryId());
            list.add(vo);
        }
        return Map.of("total", total, "page", page, "size", size, "engine", "mysql", "list", list);
    }

    private String highlight(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return text;
        }
        return text.replace(keyword, "<em>" + keyword + "</em>");
    }

    /** MySQL 分支的简介摘录：取关键词前 40 字上下文 */
    private String introSnippet(String intro, String keyword) {
        if (intro == null) {
            return "";
        }
        int idx = intro.indexOf(keyword == null ? "" : keyword);
        int from = Math.max(idx - 15, 0);
        int to = Math.min(from + 60, intro.length());
        String s = intro.substring(from, to);
        if (from > 0) {
            s = "…" + s;
        }
        if (to < intro.length()) {
            s = s + "…";
        }
        return s;
    }

    private SearchProductVo hitToVo(SearchHit<com.shop.product.es.ProductDoc> hit) {
        var doc = hit.getContent();
        SearchProductVo vo = new SearchProductVo();
        vo.setProductId(doc.getProductId());
        vo.setProductName(ProductEsService.pickHighlight(hit, "productName", doc.getProductName()));
        vo.setProductTitle(ProductEsService.pickHighlight(hit, "productTitle", doc.getProductTitle()));
        vo.setProductIntro(ProductEsService.pickHighlight(hit, "productIntro",
                doc.getProductIntro() == null ? "" : doc.getProductIntro()));
        vo.setProductPicture(doc.getProductPicture());
        vo.setProductPrice(doc.getProductPrice());
        vo.setProductSellingPrice(doc.getProductSellingPrice());
        vo.setProductSales(doc.getProductSales());
        vo.setCategoryId(doc.getCategoryId());
        return vo;
    }
}