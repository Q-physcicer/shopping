package com.shop.product.service.impl;

import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.product.mapper.CarouselMapper;
import com.shop.product.pojo.Carousel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description: 轮播图服务实现类
 */
@Service
public class CarouselServiceImpl {

    @Autowired
    private CarouselMapper carouselMapper;

    public List<Carousel> getCarouselList() {
        List<Carousel> list = carouselMapper.selectList(null);

        if (list.isEmpty()) {
            throw new XmException(ExceptionEnum.GET_CAROUSEL_NOT_FOUND);
        }

        return list;
    }
}