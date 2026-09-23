package com.shopping.service.impl;

import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.CarouselMapper;
import com.shopping.pojo.Carousel;
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