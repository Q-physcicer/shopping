package com.shop.product.es;

import com.shop.product.mapper.ProductMapper;
import com.shop.product.pojo.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ES 商品搜索服务（P3）：
 * - 启动自动建索引并全量导入（索引不存在时）
 * - 全量重建入口（管理端按钮触发/P5 经内部接口调用）
 * - 高亮搜索：productName/productTitle/productIntro 三字段 ik 检索
 * 任一 ES 异常向上抛出，由 SearchService 统一做熔断标记降级 MySQL，不影响服务可用性。
 */
@Service
@Slf4j
public class ProductEsService {

    public static final String INDEX = "product_index";

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;
    @Autowired
    private ProductMapper productMapper;

    @Value("${shop.search.es-enabled:false}")
    private boolean esEnabled;

    /** 无货商品不入索引 */
    public static final int MIN_STOCK_TO_INDEX = 1;

    /**
     * 初始化：索引不存在则创建 + 全量导入（幂等）。
     * ES 未启用或连接失败仅打日志，不阻塞启动。
     */
    public void initIndexIfAbsent() {
        if (!esEnabled) {
            log.info("[ES] 搜索开关关闭，跳过索引初始化（走 MySQL 降级）");
            return;
        }
        try {
            if (elasticsearchTemplate.indexOps(IndexCoordinates.of(INDEX)).exists()) {
                return;
            }
            log.info("[ES] 索引 {} 不存在，创建并全量导入", INDEX);
            elasticsearchTemplate.indexOps(ProductDoc.class).createWithMapping();
            rebuildIndex();
        } catch (Exception e) {
            log.warn("[ES] 索引初始化失败（服务端 ES 未就绪或 ik 插件缺失），搜索将降级 MySQL：{}", e.getMessage());
        }
    }

    /** 全量重建（拉库全量导入），返回导入条数 */
    public long rebuildIndex() {
        List<Product> all = productMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .gt(Product::getProductNum, MIN_STOCK_TO_INDEX - 1));
        List<ProductDoc> docs = all.stream().map(this::toDoc).collect(Collectors.toList());
        if (!docs.isEmpty()) {
            elasticsearchTemplate.save(docs, IndexCoordinates.of(INDEX));
        }
        log.info("[ES] 全量导入完成，共 {} 条", docs.size());
        return docs.size();
    }

    /** 单条同步（商品上架/改库存改价后调用） */
    public void syncOne(Product product) {
        if (product == null) {
            return;
        }
        if (product.getProductNum() != null && product.getProductNum() < MIN_STOCK_TO_INDEX) {
            elasticsearchTemplate.delete(String.valueOf(product.getProductId()), IndexCoordinates.of(INDEX));
            return;
        }
        elasticsearchTemplate.save(toDoc(product), IndexCoordinates.of(INDEX));
    }

    public void delete(Integer productId) {
        elasticsearchTemplate.delete(String.valueOf(productId), IndexCoordinates.of(INDEX));
    }

    /**
     * 高亮分页搜索。
     * @param sort "sales"=销量降序 / "price"=售价升序 / 其它=相关度
     */
    public SearchHits<ProductDoc> search(String keyword, Integer categoryId, String sort, int page, int size) {
        Highlight highlight = new Highlight(
                        HighlightParameters.builder()
                                .withPreTags("<em>")
                                .withPostTags("</em>")
                                .build(),
                        List.of(new HighlightField("productName"),
                                new HighlightField("productTitle"),
                                new HighlightField("productIntro")));

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(mm -> mm
                        .query(keyword)
                        .fields("productName^3", "productTitle^2", "productIntro")
                        .analyzer("ik_smart")))
                .withFilter(f -> categoryId != null
                        ? f.term(t -> t.field("categoryId").value(categoryId))
                        : f.matchAll(m -> m))
                .withSort("sales".equals(sort)
                        ? Sort.by(Sort.Order.desc("productSales"))
                        : "price".equals(sort)
                                ? Sort.by(Sort.Order.asc("productSellingPrice"))
                                : Sort.by(Sort.Order.desc("_score")))
                .withPageable(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)))
                .withHighlightQuery(new HighlightQuery(highlight, ProductDoc.class))
                .build();

        return elasticsearchTemplate.search(query, ProductDoc.class, IndexCoordinates.of(INDEX));
    }

    /** 命中项的高亮文本（未命中高亮时回落原文） */
    public static String pickHighlight(SearchHit<ProductDoc> hit, String field, String fallback) {
        List<String> h = hit.getHighlightField(field);
        return (h == null || h.isEmpty()) ? fallback : h.get(0);
    }

    /** DB 实体 -> 索引文档 */
    private ProductDoc toDoc(Product p) {
        ProductDoc doc = new ProductDoc();
        doc.setProductId(p.getProductId());
        doc.setProductName(p.getProductName());
        doc.setProductTitle(p.getProductTitle());
        doc.setProductIntro(p.getProductIntro());
        doc.setCategoryId(p.getCategoryId());
        doc.setProductPicture(p.getProductPicture());
        doc.setProductPrice(p.getProductPrice());
        doc.setProductSellingPrice(p.getProductSellingPrice());
        doc.setProductSales(p.getProductSales());
        doc.setProductNum(p.getProductNum());
        return doc;
    }

    public boolean isEsEnabled() {
        return esEnabled;
    }
}