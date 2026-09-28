package com.shop.cart.controller;

import com.shop.cart.pojo.Product;
import com.shop.cart.service.impl.CollectServiceImpl;
import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收藏商品（P1 越权修复：身份从 X-User-Id 取，路径不再传 userId）
 *
 * @Description: 收藏商品
 */
@RestController
@RequestMapping("/collect")
public class CollectController {

    @Autowired
    private CollectServiceImpl csi;

    /** 将商品收藏 */
    @PostMapping("/user/{productId}")
    public Result addCollect(@PathVariable String productId) {
        String userId = requireUserId();
        csi.addCollect(userId, productId);
        return Result.success("商品收藏成功");
    }

    /** 获取用户收藏 */
    @GetMapping("/user")
    public Result getCollect() {
        String userId = requireUserId();
        List<Product> collects = csi.getCollect(userId);
        return Result.success("success", collects);
    }

    /** 删除收藏 */
    @DeleteMapping("/user/{productId}")
    public Result deleteCollect(@PathVariable String productId) {
        String userId = requireUserId();
        csi.deleteCollect(userId, productId);
        return Result.success("删除收藏成功");
    }

    private String requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return String.valueOf(uid);
    }
}