package com.shop.product.service;

import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.product.mapper.CategoryMapper;
import com.shop.product.pojo.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description: 分类服务
 */
@Service
public class CategoryServiceImpl {

    @Autowired
    private CategoryMapper categoryMapper;

    public List<Category> getAll() {
        List<Category> categories = categoryMapper.selectList(null);

        if (categories.isEmpty()) {
            throw new XmException(ExceptionEnum.GET_CATEGORY_NOT_FOUND);
        }

        return categories;
    }
}