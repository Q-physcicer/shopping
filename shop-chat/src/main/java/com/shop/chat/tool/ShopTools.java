package com.shop.chat.tool;

import com.alibaba.fastjson.JSON;
import com.shop.common.context.UserContext;
import com.shop.feign.CartClient;
import com.shop.feign.OrderClient;
import com.shop.feign.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 购物端 Agent 工具集（P4）：
 * - 全部经 Feign 调用业务服务，不直连 DB —— 复用业务校验/库存/幂等逻辑
 * - 用户身份一律从 ToolContext 取（controller 由网关注入的 X-User-Id 放入），
 *   绝不信任模型编造的 userId；写操作前二次校验登录态
 * - 同一组工具通过 MCP Server 暴露给外部 AI 客户端（ToolContext 为空 = 未登录，只读可放行）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShopTools {

    private final ProductClient productClient;
    private final CartClient cartClient;
    private final OrderClient orderClient;

    private static final String LOGIN_HINT = "当前用户尚未登录，请引导用户先登录后再继续该操作。";

    // ---------------- 浏览类（匿名可用） ----------------

    @Tool(description = "搜索星选商城在售商品。返回商品列表（含名称、售价、销量、简介）。"
            + "参数：keyword 搜索关键词（如手机/空调/Redmi），categoryId 可选分类ID，用类型 preferred 若已知；"
            + "size 每页数量，默认5")
    public String searchProducts(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "分类ID，可选", required = false) Integer categoryId,
            @ToolParam(description = "返回条数，默认5", required = false) Integer size,
            ToolContext toolContext) {
        try {
            Object data = callAsUser(toolContext, () ->
                    productClient.search(keyword, categoryId, "relevance", 1,
                            size == null ? 5 : Math.min(size, 10)).getData());
            return JSON.toJSONString(Map.of("items", data));
        } catch (Exception e) {
            log.error("[ShopTools] searchProducts 失败", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询指定商品的详细信息（名称、价格、库存、简介、分类）。参数 productId 商品ID")
    public String getProductDetail(
            @ToolParam(description = "商品ID") Integer productId,
            ToolContext toolContext) {
        try {
            return JSON.toJSONString(callAsUser(toolContext, () ->
                    productClient.getProductDetail(productId).getData()));
        } catch (Exception e) {
            return "查询商品详情失败：" + e.getMessage();
        }
    }

    // ---------------- 登录后可用（代客操作） ----------------

    @Tool(description = "将商品加入当前用户的购物车（一次加1件）。需要用户已登录。参数 productId 商品ID")
    public String addToCart(
            @ToolParam(description = "商品ID") Integer productId,
            ToolContext toolContext) {
        if (!loggedIn(toolContext)) {
            return LOGIN_HINT;
        }
        try {
            Map<String, Object> r = callAsUser(toolContext, () ->
                    cartClient.addCart(productId));
            return JSON.toJSONString(r);
        } catch (Exception e) {
            log.error("[ShopTools] addToCart 失败", e);
            return "加入购物车失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询当前用户的购物车列表（商品、数量、价格）。需要用户已登录。无参数")
    public String getMyCart(ToolContext toolContext) {
        if (!loggedIn(toolContext)) {
            return LOGIN_HINT;
        }
        try {
            return JSON.toJSONString(callAsUser(toolContext, () ->
                    cartClient.getMyCart().getData()));
        } catch (Exception e) {
            return "查询购物车失败：" + e.getMessage();
        }
    }

    @Tool(description = "帮当前用户直接下单购买某商品（不走购物车）。需要用户已登录。"
            + "会真实生成订单，30分钟内未支付将自动取消。务必先向用户确认购买意向与数量再调用。"
            + "参数：productId 商品ID，num 购买数量(1-5，超界会被钳制到1-5)")
    public String placeOrder(
            @ToolParam(description = "商品ID") Integer productId,
            @ToolParam(description = "购买数量1-5") Integer num,
            ToolContext toolContext) {
        if (!loggedIn(toolContext)) {
            return LOGIN_HINT;   // 模型幻觉兜底：没登录绝不下单
        }
        // 钳制数量到 1-5：防模型幻觉给出 0/99 等非法值；服务端 /order/direct 仍保留权威校验
        int n = (num == null) ? 1 : Math.min(Math.max(num, 1), 5);
        try {
            Object result = callAsUser(toolContext, () ->
                    orderClient.placeOrderDirect(
                            new com.shop.feign.DirectOrderRequest(productId, n)).getData());
            log.info("[ShopTools] Agent 代客下单成功 userId={} productId={}x{}",
                    toolContext.getContext().get("userId"), productId, n);
            return JSON.toJSONString(result);
        } catch (Exception e) {
            log.error("[ShopTools] placeOrder 失败", e);
            return "下单失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询当前用户的订单列表。需要用户已登录。无参数")
    public String getMyOrders(ToolContext toolContext) {
        if (!loggedIn(toolContext)) {
            return LOGIN_HINT;
        }
        try {
            return JSON.toJSONString(callAsUser(toolContext, () ->
                    orderClient.getMyOrders().getData()));
        } catch (Exception e) {
            return "查询订单失败：" + e.getMessage();
        }
    }

    // ---------------- 售后（P8 仅退款闭环） ----------------

    @Tool(description = "为当前用户的指定订单提交售后申请（仅退款，无退货物流环节）。"
            + "参数：orderId 订单号，productId 商品ID（先调 getMyOrders 确认用户确有该订单），reason 申请理由（用用户原话）。"
            + "仅限已支付订单、支付后7天内可申请；若不可申请，工具返回的提示里会写明原因（如待支付/已取消/超期/重复申请）。"
            + "这是真实操作，务必先向用户确认商品与申请理由后再调用。")
    public String applyAfterSale(
            @ToolParam(description = "订单号") String orderId,
            @ToolParam(description = "商品ID") Integer productId,
            @ToolParam(description = "申请理由（用户原话）") String reason,
            ToolContext toolContext) {
        if (!loggedIn(toolContext)) {
            return LOGIN_HINT;
        }
        try {
            Object result = callAsUser(toolContext, () ->
                    orderClient.applyAftersale(new com.shop.feign.AftersaleApplyRequest(orderId, productId, reason)));
            log.info("[ShopTools] Agent 代客提交售后 userId={} orderId={} productId={}",
                    toolContext.getContext().get("userId"), orderId, productId);
            return JSON.toJSONString(result);
        } catch (Exception e) {
            log.error("[ShopTools] applyAfterSale 失败", e);
            return "提交售后申请失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询当前用户的售后申请列表与处理进度。返回每条售后的单号、订单号、商品、退款金额、状态（0待处理/1已退款完成/2已拒绝，拒绝时含拒绝理由）。需要用户已登录。无参数")
    public String getMyAfterSales(ToolContext toolContext) {
        if (!loggedIn(toolContext)) {
            return LOGIN_HINT;
        }
        try {
            return JSON.toJSONString(callAsUser(toolContext, () ->
                    orderClient.getMyAftersales().getData()));
        } catch (Exception e) {
            return "查询售后进度失败：" + e.getMessage();
        }
    }

    /** 身份校验：ToolContext 里必须有网关注入的 userId（外部 MCP 匿名客户端没有） */
    private boolean loggedIn(ToolContext toolContext) {
        return extractUserId(toolContext) != null;
    }

    /**
     * SSE 的模型工具调用发生在 reactor 工作线程，网关注入的 ThreadLocal 不会传播到这里；
     * 因此以 toolContext 为身份权威来源，在**当前工具线程**上重建 UserContext，
     * 供 FeignIdentityInterceptor 在同线程发出 Feign 调用时读取并透传 X-User-*。
     */
    private <T> T callAsUser(ToolContext toolContext, Supplier<T> fn) {
        Long uid = extractUserId(toolContext);
        if (uid == null) {
            return fn.get();   // 匿名：只读调用可放行，写操作在下游被网关语义拒绝
        }
        Object username = toolContext.getContext().get("username");
        Object role = toolContext.getContext().get("role");
        UserContext.set(new UserContext.Principal(uid,
                username == null ? null : String.valueOf(username),
                role == null ? "USER" : String.valueOf(role)));
        try {
            return fn.get();
        } finally {
            UserContext.clear();
        }
    }

    private Long extractUserId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object uid = toolContext.getContext().get("userId");
        if (uid == null || "anonymous".equals(String.valueOf(uid))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(uid));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}