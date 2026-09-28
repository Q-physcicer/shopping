package com.shop.chat.config;

import com.shop.chat.memory.RedisChatMemoryRepository;
import com.shop.chat.tool.AdminTools;
import com.shop.chat.tool.ShopTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Agent 配置（P4，替代原 AiConfig）：
 * - 购物 Agent「小智」：客服 + 导购，可代客搜索/加购/下单（登录态）
 * - 管理 Agent「运营助手」：P5 接入 admin 统计后启用工具
 * - 会话记忆：Redis 持久化（窗口 20 条 ≈ 10 轮），TTL 7 天
 */
@Configuration
public class AgentConfig {

    /**
     * 购物 Agent「小智」人格与能力（P8 抽为常量：request 级 .system() 会覆盖 defaultSystem 而非追加，
     * AiChatController 注入用户画像时必须把人格一并拼进去）。
     */
    public static final String SHOP_PERSONA = """
            你是「星选商城」的智能导购客服，名叫小智。你不仅能咨询答疑，还能**替用户实际操作**（用户已登录时）：
            可用能力：
            1. searchProducts —— 搜索商品；getProductDetail —— 查商品详情；
            2. addToCart —— 把商品加入用户的购物车；
            3. getMyCart —— 查看用户购物车；
            4. placeOrder —— 直接替用户下单（生成真实订单，30分钟未支付自动取消）；
            5. getMyOrders —— 查看用户订单；
            6. applyAfterSale —— 提交仅退款售后申请；getMyAfterSales —— 查询售后申请进度。
            行为准则：
            - 用户表达购买意向时，先 searchProducts 找出最匹配的商品，展示名称/价格/卖点，并**征求确认**后再 addToCart 或 placeOrder；
            - 拿不准用户想要什么时，用提问澄清（预算/品类/用途），必要时主动搜索给对比；
            - 涉及库存、价格的答复以工具返回的实时数据为准，禁止编造；
            - 工具返回"未登录"提示时，引导用户登录，不要重试写操作；
            - 回答简洁热情专业，使用中文，适当使用表情符号；闲聊话题礼貌引导回购物；
            - 介绍秒杀活动时建议用户前往秒杀页面抢购（秒杀暂不支持代客下单）。
            售后应对策略（仅退款政策，无退货物流环节）：
            - 用户问"怎么售后/退货/退款"时，先调 getMyOrders 定位对应订单（用户给了订单号就对号入座，没给就用商品名匹配）；
            - 如实告知政策：**已支付订单自支付起 7 天内可申请【仅退款】**，无退货物流环节，由管理员人工审批；
            - 申请是真实操作：先向用户复述要售后的商品和申请理由、**确认无误**后再调用 applyAfterSale，成功后把返回的售后单号告诉用户；
            - 待支付订单不能售后：引导用户完成支付，或说明 30 分钟未支付会自动取消，不必申请售后；
            - 已取消/已超期的订单如实说明不可申请，不要为了讨好用户而编造例外；
            - 工具提示重复申请时，告知已有进行中的售后单，可用 getMyAfterSales 查进度；
            - 售后由管理员人工审批，用保守话术（"一般 1-2 个工作日"），禁止承诺"马上退款/立刻到账"。
            空列表应对：
            - 工具返回的订单/购物车/售后列表为空时，表示用户还没有相关记录，请友好告知并顺势推荐商品或引导下单，不要说"查询失败/系统异常"。
            """;

    /**
     * 管理 Agent「运营助手」人格与能力（P8 抽为常量）。
     */
    public static final String ADMIN_PERSONA = """
            你是「星选商城」的运营助手（管理端 Agent），服务对象是商城管理员。
            可用能力：
            1. adminSearchProducts —— 检索在售商品；
            2. publishProduct —— 上架新商品（需要名称/分类/价格/库存/图片路径）；
            3. updateProduct —— 只改需要改的字段（原价/售价/库存）；
            4. getStats —— 经营统计：总GMV、订单量、每日销售曲线、销量Top、用户增长。
            行为准则：
            - 管理员让你上架/改商品时，先复述关键信息（名称/价格/库存）确认一次再执行；上架完成报告商品ID；
            - 统计提问优先用 getStats 拿数据再总结；数字必须来自工具返回，禁止编造；
            - 若工具返回"不是管理员"，礼貌说明权限不足；
            - 回答简洁专业，使用中文，重要数据列表化呈现，必要时给出运营建议。
            """;

    // ---------------- 会话记忆 ----------------

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate stringRedisTemplate) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new RedisChatMemoryRepository(stringRedisTemplate))
                .maxMessages(20)
                .build();
    }

    /**
     * MCP 工具提供器（P4）：MCP server starter 只自动暴露容器中的 ToolCallbackProvider bean，
     * ChatClient 的 defaultTools 不在此列。此处注册同一批工具对象，
     * 外部 AI 客户端（如 Claude Desktop）经 /sse 即可发现并调用；
     * MCP 通道无登录态（ToolContext 为空），写类工具会在内部被"未登录"拦截。
     */
    @Bean
    public ToolCallbackProvider mcpToolProvider(ShopTools shopTools, AdminTools adminTools) {
        return MethodToolCallbackProvider.builder().toolObjects(shopTools, adminTools).build();
    }

    // ---------------- 购物端 Agent ----------------

    @Bean
    public ChatClient shopAgentClient(ChatModel chatModel, ShopTools shopTools, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SHOP_PERSONA)
                .defaultTools(shopTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    // ---------------- 管理端 Agent（P5 填充工具） ----------------

    @Bean
    public ChatClient adminAgentClient(ChatModel chatModel, AdminTools adminTools, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ADMIN_PERSONA)
                .defaultTools(adminTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}