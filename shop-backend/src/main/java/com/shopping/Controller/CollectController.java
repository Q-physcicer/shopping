package com.shopping.Controller;

import com.shopping.pojo.Product;
import com.shopping.service.impl.CollectServiceImpl;
import com.shopping.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Description: 收藏商品
 */
@RestController
@RequestMapping("/collect")
public class CollectController {

    @Autowired
    private CollectServiceImpl csi;

    /**
     * 将商品收藏
     * @param userId
     * @param productId
     * @return
     */
    @PostMapping("/user/{productId}/{userId}") //  收藏商品
    public Result addCollect(@PathVariable String userId, @PathVariable String productId) {
        csi.addCollect(userId, productId);
        return Result.success("商品收藏成功");
    }

    /**
     * 获取用户收藏
     * @param userId
     * @return 返回商品集合
     */
    @GetMapping("/user/{userId}") //  获取用户收藏
    public Result getCollect(@PathVariable String userId) {
        List<Product> collects = csi.getCollect(userId);
        return Result.success("success", collects);

    }

    @DeleteMapping("/user/{productId}/{userId}") //  删除收藏
    public Result deleteCollect(@PathVariable String productId, @PathVariable String userId) {
        csi.deleteCollect(userId, productId);
        return Result.success("删除收藏成功");
    }
}
