package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.order.mapper.UserMessageMapper;
import com.shop.order.pojo.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户消息服务（消息中心）。触发点：支付成功 / 超时取消 / 售后审批结果。
 */
@Service
public class UserMessageServiceImpl {

    @Autowired
    private UserMessageMapper messageMapper;

    /** 我的消息列表（最近优先；封顶 100 条防老用户无限累积拖慢列表） */
    public List<UserMessage> listByUser(Integer userId) {
        return messageMapper.selectList(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .orderByDesc(UserMessage::getId)
                .last("LIMIT 100"));
    }

    /** 未读消息数 */
    public long unreadCount(Integer userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0));
    }

    /** 标记单条已读 */
    public boolean markRead(Integer userId, Long id) {
        return messageMapper.update(null, new LambdaUpdateWrapper<UserMessage>()
                .eq(UserMessage::getId, id)
                .eq(UserMessage::getUserId, userId)
                .set(UserMessage::getIsRead, 1)) > 0;
    }

    /** 全部已读（返回实际标记条数；原 >=0 恒真，无任何未读时也算"成功"误导口径） */
    public int markAllRead(Integer userId) {
        return messageMapper.update(null, new LambdaUpdateWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0)
                .set(UserMessage::getIsRead, 1));
    }

    /** 删除单条 */
    public boolean delete(Integer userId, Long id) {
        return messageMapper.delete(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getId, id)
                .eq(UserMessage::getUserId, userId)) > 0;
    }

    /** 写入一条消息（供支付/取消/售后触发点调用） */
    public void push(Integer userId, String type, String title, String content) {
        UserMessage msg = new UserMessage();
        msg.setUserId(userId);
        msg.setType(type);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setIsRead(0);
        msg.setCreatedTime(System.currentTimeMillis());
        messageMapper.insert(msg);
    }
}
