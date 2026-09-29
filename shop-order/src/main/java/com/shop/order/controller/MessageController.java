package com.shop.order.controller;

import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import com.shop.order.service.impl.UserMessageServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息中心接口（购物端）。
 * 用户身份一律 UserContext（网关注入的 X-User-Id），禁止路径传 userId。
 */
@RestController
@RequestMapping("/order/message")
public class MessageController {

    @Autowired
    private UserMessageServiceImpl messageService;

    /** 我的消息列表 */
    @GetMapping
    public Result list() {
        return Result.success("success", messageService.listByUser(requireUserId()));
    }

    /** 未读消息数 */
    @GetMapping("/unread")
    public Result unread() {
        return Result.success("success", Map.of("count", messageService.unreadCount(requireUserId())));
    }

    /** 标记单条已读 */
    @PostMapping("/read/{id}")
    public Result read(@PathVariable Long id) {
        return messageService.markRead(requireUserId(), id)
                ? Result.success("success") : Result.fail("消息不存在", null);
    }

    /** 全部已读（返回实际标记数；0 条未读时提示口径如实） */
    @PostMapping("/readAll")
    public Result readAll() {
        int n = messageService.markAllRead(requireUserId());
        return Result.success(n > 0 ? "已标记 " + n + " 条未读消息" : "没有未读消息");
    }

    /** 删除单条 */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return messageService.delete(requireUserId(), id)
                ? Result.success("删除成功") : Result.fail("消息不存在", null);
    }

    private Integer requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return uid.intValue();
    }
}
