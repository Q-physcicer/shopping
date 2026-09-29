package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.mapper.ProductPictureMapper;
import com.shop.product.pojo.Product;
import com.shop.product.pojo.ProductPicture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description: 商品图片服务实现类
 */
@Service
public class ProductPictureServiceImpl {

    @Autowired
    private ProductPictureMapper productPictureMapper;

    @Autowired
    private ProductMapper productMapper;

    public List<ProductPicture> getProductPictureByProductId(String productId) {
        int pid = Integer.parseInt(productId);
        LambdaQueryWrapper<ProductPicture> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductPicture::getProductId, pid);

        // 执行查询
        List<ProductPicture> list = productPictureMapper.selectList(wrapper);

        if (list.isEmpty()) {
            // 百货化后 product_picture 表不再种子（P11），全新环境无多图数据：
            // 回退用商品主图合成一条，保证详情页有图可显，而非抛异常空一片
            Product product = productMapper.selectById(pid);
            if (product != null && product.getProductPicture() != null && !product.getProductPicture().isBlank()) {
                ProductPicture fallback = new ProductPicture();
                fallback.setProductId(pid);
                fallback.setProductPicture(product.getProductPicture());
                fallback.setIntro(product.getProductName());
                return List.of(fallback);
            }
        }
        return list;
    }
}
