package com.shopping.Controller;

import com.shopping.pojo.Category;
import com.shopping.service.CategoryServiceImpl;
import com.shopping.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Description: 获取商品分类
 */
@RestController
@RequestMapping("/category")
public class CategoryController {
    
    @Autowired
    private CategoryServiceImpl cgsi;

    @GetMapping("") // 获取商品分类
    public Result category() {
        List<Category> categories = cgsi.getAll();
        return Result.success("success", categories);
    }

}
