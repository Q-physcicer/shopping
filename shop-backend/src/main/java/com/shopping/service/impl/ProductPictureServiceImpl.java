package com.shopping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.ProductPictureMapper;
import com.shopping.pojo.ProductPicture;
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

    public List<ProductPicture> getProductPictureByProductId(String productId) {
        LambdaQueryWrapper<ProductPicture> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductPicture::getProductId, Integer.parseInt(productId));

        // 执行查询
        List<ProductPicture> list = productPictureMapper.selectList(wrapper);

        if (list.isEmpty()) {
            throw new XmException(ExceptionEnum.GET_PRODUCT_PICTURE_NOT_FOUND);
        }
        return list;
    }
}
