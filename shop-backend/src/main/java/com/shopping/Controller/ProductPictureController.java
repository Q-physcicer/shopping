package com.shopping.Controller;

import com.shopping.pojo.ProductPicture;
import com.shopping.service.impl.ProductPictureServiceImpl;
import com.shopping.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Auther: wdd
 * @Date: 2020-03-19 13:27
 * @Description:
 */
@RestController
@RequestMapping("/productPicture")
public class ProductPictureController {


    @Autowired
    private ProductPictureServiceImpl ppsi;

    @GetMapping("/product/{productId}")
    public Result productPicture(@PathVariable String productId) {
        List<ProductPicture> products = ppsi.getProductPictureByProductId(productId);
        return Result.success("success",  products);
    }

}
