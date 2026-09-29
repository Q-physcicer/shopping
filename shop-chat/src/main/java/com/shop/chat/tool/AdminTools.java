package com.shop.chat.tool;

import com.alibaba.fastjson.JSON;
import com.shop.common.context.UserContext;
import com.shop.feign.AdminClient;
import com.shop.feign.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 管理端 Agent 工具集（P5 补全）：
 * - 上架商品 / 改价改库存 / 统计看板 —— 经 Feign 调 admin 服务聚合接口
 * - 角色硬校验：toolContext.role != ADMIN 一律拒绝（admin Controller 依赖网关鉴权，
 *   chat 直连 Feign 不经网关，故必须在工具内二次校验，防止普通用户诱导 Agent 越权）
 * - MCP 外部通道无身份（role 为空）→ 管理写工具同样被拦截
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminTools {

    private final ProductClient productClient;
    private final AdminClient adminClient;

    private static final String FORBIDDEN = "当前用户不是管理员，无权执行管理操作。";

    // ---------------- 只读（运营参考，不做角色强校验） ----------------

    @Tool(description = "运营视角检索商品（供管理员了解在售情况）。参数：keyword 关键词，size 条数默认5")
    public String adminSearchProducts(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "返回条数", required = false) Integer size,
            ToolContext toolContext) {
        try {
            Object data = productClient.search(keyword, null, "sales", 1,
                    size == null ? 5 : Math.min(size, 20)).getData();
            return JSON.toJSONString(Map.of("items", data));
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }
    }

    // ---------------- 管理写操作（硬校验 ADMIN） ----------------

    @Tool(description = "上架一个新商品到商城（仅管理员）。参数：productName 名称、categoryId 分类ID、"
            + "productPrice 原价、productSellingPrice 售价、productNum 初始库存、productPicture 图片路径、productIntro 卖点简介")
    public String publishProduct(
            @ToolParam(description = "商品名称") String productName,
            @ToolParam(description = "分类ID") Integer categoryId,
            @ToolParam(description = "原价") Double productPrice,
            @ToolParam(description = "售价") Double productSellingPrice,
            @ToolParam(description = "库存数") Integer productNum,
            @ToolParam(description = "商品图片路径") String productPicture,
            @ToolParam(description = "卖点简介", required = false) String productIntro,
            ToolContext toolContext) {
        if (!isAdmin(toolContext)) {
            return FORBIDDEN;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("productName", productName);
            body.put("categoryId", categoryId);
            body.put("productPrice", productPrice);
            body.put("productSellingPrice", productSellingPrice);
            body.put("productNum", productNum);
            body.put("productPicture", productPicture);
            if (productIntro != null) {
                body.put("productIntro", productIntro);
            }
            body.put("productTitle", productName.length() > 28 ? productName.substring(0, 28) : productName);
            return JSON.toJSONString(callAs(toolContext, () -> adminClient.publishProduct(body)));
        } catch (Exception e) {
            log.error("[AdminTools] publishProduct 失败", e);
            return "上架失败：" + e.getMessage();
        }
    }

    @Tool(description = "修改商品价格或库存（仅管理员）。参数：productId 商品ID；"
            + "productPrice 原价(可选)、productSellingPrice 售价(可选)、productNum 库存(可选)，只传要改的字段")
    public String updateProduct(
            @ToolParam(description = "商品ID") Integer productId,
            @ToolParam(description = "新原价", required = false) Double productPrice,
            @ToolParam(description = "新售价", required = false) Double productSellingPrice,
            @ToolParam(description = "新库存", required = false) Integer productNum,
            ToolContext toolContext) {
        if (!isAdmin(toolContext)) {
            return FORBIDDEN;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            if (productPrice != null) {
                body.put("productPrice", productPrice);
            }
            if (productSellingPrice != null) {
                body.put("productSellingPrice", productSellingPrice);
            }
            if (productNum != null) {
                body.put("productNum", productNum);
            }
            if (body.isEmpty()) {
                return "没有要修改的字段";
            }
            return JSON.toJSONString(callAs(toolContext, () -> adminClient.updateProduct(productId, body)));
        } catch (Exception e) {
            log.error("[AdminTools] updateProduct 失败", e);
            return "修改失败：" + e.getMessage();
        }
    }

    // ---------------- 统计 ----------------

    @Tool(description = "查询商场经营统计数据（仅管理员）：总GMV、订单量、每日销售曲线、销量Top商品、用户总数及增长曲线。参数 days 统计天数默认7")
    public String getStats(
            @ToolParam(description = "统计天数", required = false) Integer days,
            ToolContext toolContext) {
        if (!isAdmin(toolContext)) {
            return FORBIDDEN;
        }
        try {
            Object data = callAs(toolContext, () -> adminClient.overview(days == null ? 7 : days).getData());
            return JSON.toJSONString(data);
        } catch (Exception e) {
            log.error("[AdminTools] getStats 失败", e);
            return "统计查询失败：" + e.getMessage();
        }
    }

    // ---------------- 身份工具 ----------------

    private boolean isAdmin(ToolContext toolContext) {
        if (toolContext == null) {
            return false;
        }
        Object role = toolContext.getContext().get("role");
        return "ADMIN".equals(role);
    }

    /** reactor 工具线程上重建 UserContext 供 Feign 透传（同 ShopTools 的桥接策略） */
    private <T> T callAs(ToolContext toolContext, Supplier<T> fn) {
        Object uid = toolContext.getContext().get("userId");
        Object username = toolContext.getContext().get("username");
        Object role = toolContext.getContext().get("role");
        if (uid == null || "anonymous".equals(String.valueOf(uid))) {
            return fn.get();
        }
        // P2：畸形 uid 防御（对齐 ShopTools.extractUid：工具线程上 Long.valueOf 抛 NFE 会污染整轮对话）
        Long uidVal;
        try {
            uidVal = Long.valueOf(String.valueOf(uid));
        } catch (NumberFormatException e) {
            return fn.get();   // 无法解析时按匿名降级（下游 ADMIN 校验会拒）
        }
        UserContext.set(new UserContext.Principal(uidVal,
                username == null ? null : String.valueOf(username),
                role == null ? "USER" : String.valueOf(role)));
        try {
            return fn.get();
        } finally {
            UserContext.clear();
        }
    }
}