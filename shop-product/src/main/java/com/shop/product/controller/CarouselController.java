package com.shop.product.controller;


import com.shop.product.pojo.Carousel;
import com.shop.product.service.impl.CarouselServiceImpl;
import com.shop.common.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Description: 首页轮播图
 */
@RestController
public class CarouselController {
    
    @Autowired
    private CarouselServiceImpl casi;

    @GetMapping("/resources/carousel") // 首页轮播图
    public Result carousels() {
        List<Carousel> carousels = casi.getCarouselList();
        return Result.success("success", carousels);
    }

}
