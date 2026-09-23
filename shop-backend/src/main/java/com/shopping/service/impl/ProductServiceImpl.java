package com.shopping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.ProductMapper;
import com.shopping.pojo.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @Description: 商品服务实现类
 */
@Service
public class ProductServiceImpl {

        @Autowired
        private ProductMapper productMapper;

        public List<Product> getProductByCategoryId(Integer categoryId) {
                // 构造查询条件并按销量降序
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Product::getCategoryId, categoryId)
                        .orderByDesc(Product::getProductSales);

                // 分页查询前8条
                Page<Product> page = new Page<>(1, 8);
                IPage<Product> productPage = productMapper.selectPage(page, wrapper);
                List<Product> list = productPage.getRecords();

                if (list.isEmpty()) {
                        throw new XmException(ExceptionEnum.GET_PRODUCT_NOT_FOUND);
                }

                return list;
        }

        public List<Product> getHotProduct() {
                // 按销量降序查询前8条
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                wrapper.orderByDesc(Product::getProductSales);

                Page<Product> page = new Page<>(1, 8);
                IPage<Product> productPage = productMapper.selectPage(page, wrapper);
                List<Product> list = productPage.getRecords();

                if (list.isEmpty()) {
                        throw new XmException(ExceptionEnum.GET_PRODUCT_NOT_FOUND);
                }

                return list;
        }

        public Product getProductById(String productId) {
                // 根据ID查询商品
                Product product = productMapper.selectById(productId);

                if (product == null) {
                        throw new XmException(ExceptionEnum.GET_PRODUCT_NOT_FOUND);
                }

                return product;
        }

        public IPage<Product> getProductByPage(String currentPage, String pageSize, String categoryId) {
                // 构造分页参数
                long pageNum = Long.parseLong(currentPage);
                long size = Long.parseLong(pageSize);
                Page<Product> page = new Page<>(pageNum, size);

                // 构造查询条件
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                if (!Objects.equals(categoryId, "0")) {
                        wrapper.eq(Product::getCategoryId, categoryId);
                }

                // 执行分页查询
                return productMapper.selectPage(page, wrapper);
        }
}