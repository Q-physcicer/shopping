package com.shopping.Controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shopping.pojo.Product;
import com.shopping.service.impl.ProductServiceImpl;
import com.shopping.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 商品控制器
 */
@RestController
@RequestMapping("/product")
public class ProductController {
    
    @Autowired
    private ProductServiceImpl pdsi;

    @GetMapping("/category/limit/{categoryId}") //  获取商品
    public Result getProductByCategoryId(@PathVariable Integer categoryId) {
        List<Product> list = pdsi.getProductByCategoryId(categoryId);
        return Result.success("success",list);

    }

    @GetMapping("/category/hot") //  获取热门商品
    public Result getHotProduct() {
        List<Product> list = pdsi.getHotProduct();
        return Result.success("success", list);

    }

    @GetMapping("/{productId}") //  获取商品
    public Result getProduct(@PathVariable String productId) {
        Product product = pdsi.getProductById(productId);
        return Result.success("success", product);
    }

    @GetMapping("/page/{currentPage}/{pageSize}/{categoryId}") //  获取商品分页
    public Map<String, Object> getProductByPage(@PathVariable String currentPage, @PathVariable String pageSize, @PathVariable String categoryId) {
        // 调用服务层获取分页数据（返回类型改为IPage）
        IPage<Product> page = pdsi.getProductByPage(currentPage, pageSize, categoryId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "001");
        result.put("data", page.getRecords());
        result.put("total", page.getTotal());
        return result;
    }


}
