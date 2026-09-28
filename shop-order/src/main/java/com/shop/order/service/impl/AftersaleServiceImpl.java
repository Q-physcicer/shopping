package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.util.IdWorker;
import com.shop.common.util.Result;
import com.shop.order.mapper.AftersaleMapper;
import com.shop.order.pojo.AftersaleRecord;
import com.shop.order.pojo.Order;
import com.shop.order.vo.AftersaleVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 售后服务（P8 仅退款闭环）：
 * - 政策：已支付订单（status 1/3）自支付起 7 天内可申请仅退款；待支付/已取消不可申请
 * - 防重复：事务内 FOR UPDATE 锁父订单行，串行化同一行的并发申请（MySQL 无 partial index，
 *   不能对 (order_row_id, status=0) 建唯一键——终态重申请会撞键）
 * - 审批：CAS（WHERE status=0），双管理员并发只赢一个
 * - 政策性拒绝返回 Result.fail(语义化 msg) 供 AI Agent 转述，不抛异常
 */
@Service
public class AftersaleServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(AftersaleServiceImpl.class);

    /** 售后申请窗口：默认支付后 7 天 */
    @Value("${shop.aftersale.apply-window-days:7}")
    private int applyWindowDays;

    /** 售后单号生成器（workerId=2 与 OrderServiceImpl(1,1) 错开，防雪花撞号） */
    private final IdWorker idWorker = new IdWorker(2, 1);

    @Autowired
    private AftersaleMapper aftersaleMapper;

    /**
     * 提交售后申请（仅退款）。政策违规返回 code=0 的语义化提示（Agent 可直接转述）。
     */
    @Transactional
    public Result apply(Integer userId, String orderId, Integer productId, String reason) {
        if (orderId == null || orderId.isBlank() || productId == null) {
            return Result.fail("缺少订单号或商品信息，无法提交售后申请", null);
        }
        if (reason == null || reason.isBlank()) {
            return Result.fail("请说明申请售后的理由", null);
        }

        // 行锁父订单行：串行化同一订单行的并发申请，再做政策与重复校验
        Order row = aftersaleMapper.lockOrderRow(orderId, productId, userId);
        if (row == null) {
            return Result.fail("订单不存在或不属于当前用户，请核对订单号与商品", null);
        }
        if (row.getStatus() == Order.STATUS_PENDING) {
            return Result.fail("该订单尚未支付：可继续完成支付，30 分钟内未支付将自动取消，无需申请售后", null);
        }
        if (row.getStatus() == Order.STATUS_CANCELLED) {
            return Result.fail("该订单已取消，无需申请售后", null);
        }

        // 已支付/已完成：校验支付后 7 天申请窗口
        long baseTime = row.getPayTime() != null ? row.getPayTime() : row.getOrderTime();
        long windowMs = applyWindowDays * 24L * 3600 * 1000;
        if (baseTime + windowMs < System.currentTimeMillis()) {
            return Result.fail("该订单已超过可申请售后期（支付后 " + applyWindowDays + " 天内），无法受理", null);
        }

        // 防重复：同一订单行存在进行中的售后单
        Long pending = aftersaleMapper.selectCount(new LambdaQueryWrapper<AftersaleRecord>()
                .eq(AftersaleRecord::getOrderRowId, row.getId())
                .eq(AftersaleRecord::getStatus, AftersaleRecord.STATUS_PENDING));
        if (pending != null && pending > 0) {
            return Result.fail("该订单行已有进行中的售后申请，请耐心等待处理（可在客服处查询进度）", null);
        }

        AftersaleRecord record = new AftersaleRecord();
        record.setAftersaleId(idWorker.nextId() + "");
        record.setOrderId(orderId);
        record.setOrderRowId(row.getId());
        record.setUserId(userId);
        record.setProductId(productId);
        record.setProductNum(row.getProductNum());
        record.setRefundAmount(round(row.getProductPrice() * row.getProductNum()));
        record.setReason(reason.length() > 200 ? reason.substring(0, 200) : reason);
        record.setStatus(AftersaleRecord.STATUS_PENDING);
        record.setApplyTime(System.currentTimeMillis());
        aftersaleMapper.insert(record);

        log.info("[AfterSale] 售后申请已提交 aftersaleId={} orderId={} productId={} userId={} amount={}",
                record.getAftersaleId(), orderId, productId, userId, record.getRefundAmount());
        return Result.success("售后申请已提交，管理员一般 1-2 个工作日内处理",
                Map.of("aftersaleId", record.getAftersaleId()));
    }

    /** 管理端审批（approve=true 同意 / false 拒绝）。CAS 保证并发审批只赢一个。 */
    public Result handle(Integer handlerId, String aftersaleId, boolean approve, String rejectReason) {
        if (aftersaleId == null || aftersaleId.isBlank()) {
            return Result.fail("缺少售后单号", null);
        }
        if (approve) {
            int updated = aftersaleMapper.approveIfPending(aftersaleId, System.currentTimeMillis(), handlerId);
            if (updated == 0) {
                return Result.fail("该售后单不存在或已被处理", null);
            }
            log.info("[AfterSale] 售后已同意退款 aftersaleId={} handler={}", aftersaleId, handlerId);
            return Result.success("已同意退款（模拟退款完成）", null);
        }
        if (rejectReason == null || rejectReason.isBlank()) {
            return Result.fail("拒绝售后必须填写理由", null);
        }
        int updated = aftersaleMapper.rejectIfPending(aftersaleId,
                rejectReason.length() > 200 ? rejectReason.substring(0, 200) : rejectReason,
                System.currentTimeMillis(), handlerId);
        if (updated == 0) {
            return Result.fail("该售后单不存在或已被处理", null);
        }
        log.info("[AfterSale] 售后已拒绝 aftersaleId={} handler={} reason={}", aftersaleId, handlerId, rejectReason);
        return Result.success("已拒绝该售后申请", null);
    }

    /** 我的售后列表（无记录返回空列表，供 Agent 友好转述） */
    public List<AftersaleVo> myAftersales(Integer userId) {
        return aftersaleMapper.listByUser(userId);
    }

    /** 管理端分页查询 */
    public Map<String, Object> pageAftersales(int page, int size, Integer status) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(page, 1);
        Long total = aftersaleMapper.countByCond(status);
        List<AftersaleVo> list = aftersaleMapper.pageByCond(status, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total == null ? 0 : total);
        data.put("list", list);
        return data;
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}