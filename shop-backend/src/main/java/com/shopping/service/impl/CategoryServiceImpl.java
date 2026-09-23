package com.shopping.service;

import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.CategoryMapper;
import com.shopping.pojo.Category;
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