package com.shop.product.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sentinel 限流规则（P2 秒杀高并发）：
 * 1. 接口级 QPS 1000/单机（抢购入口整体限流）
 * 2. 热点参数 seckillId 单维度 200 QPS（防单个活动被打爆）
 * 规则当前以代码形式随服务启动加载管理；后续可迁 Nacos 持久化（dataId: shop-product-sentinel-flow.json）。
 */
@Configuration
public class SentinelRulesConfig {

    public static final String SECKILL_RESOURCE = "seckillProduct";

    @PostConstruct
    public void initRules() {
        // 接口级 QPS
        FlowRule flowRule = new FlowRule();
        flowRule.setResource(SECKILL_RESOURCE);
        flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flowRule.setCount(1000);
        List<FlowRule> flowRules = new ArrayList<>();
        flowRules.add(flowRule);
        FlowRuleManager.loadRules(flowRules);

        // 热点参数（seckillId，方法参数下标 0）单维度限流
        ParamFlowRule paramRule = new ParamFlowRule();
        paramRule.setResource(SECKILL_RESOURCE);
        paramRule.setParamIdx(0);
        paramRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        paramRule.setCount(200);
        paramRule.setDurationInSec(1);
        // burst 容忍短时突发
        paramRule.setBurstCount(50);
        ParamFlowRuleManager.loadRules(Collections.singletonList(paramRule));
    }
}