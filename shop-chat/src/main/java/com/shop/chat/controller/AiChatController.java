package com.shop.chat.controller;

import com.shop.chat.config.AgentConfig;
import com.shop.chat.profile.UserProfileService;
import com.shop.common.context.UserContext;
import com.shop.common.util.Result;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 双 Agent 接口（P4，P8 加画像注入/停止生成）：
 * GET  /chat/stream           购物端 Agent「小智」SSE 流式（身份登录后可代客下单）
 * POST /chat                  购物端非流式（保底）
 * POST /chat/reset            清空当前用户会话
 * GET  /agent/admin/stream    管理端 Agent SSE（网关仅 ADMIN 可达）
 *
 * 身份传递：网关 AuthGlobalFilter 校验 JWT 后注入 X-User-Id/X-User-Role，
 * controller 将其放入 ChatClient.prompt().toolContext()，工具方法用 ToolContext 取——
 * 模型永远无法编造 userId；匿名访问（无 JWT）时 toolContext 中 userId=anonymous。
 * 会话隔离：记忆键 = u{userId}:{conversationId}，防止跨用户读取会话上下文。
 * 用户画像（P8）：每轮流结束异步提炼偏好写 Redis，下一轮拼进 system prompt，跨会话生效。
 */
@RestController
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private static final String DONE_SIGNAL = "[DONE]";

    private final ChatClient shopAgentClient;
    private final ChatClient adminAgentClient;
    private final ChatMemory chatMemory;
    private final UserProfileService profileService;

    public AiChatController(ChatClient shopAgentClient, ChatClient adminAgentClient,
                            ChatMemory chatMemory, UserProfileService profileService) {
        this.shopAgentClient = shopAgentClient;
        this.adminAgentClient = adminAgentClient;
        this.chatMemory = chatMemory;
        this.profileService = profileService;
    }

    /** 购物端 Agent SSE 流式对话 */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message,
                                                     @RequestParam(required = false) String conversationId) {
        return stream(shopAgentClient, AgentConfig.SHOP_PERSONA, message, memoryKey("shop", conversationId));
    }

    /** 管理端 Agent SSE 流式对话（P5 与 React 管理端对话面板联动） */
    @GetMapping(value = "/agent/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> adminChatStream(@RequestParam String message,
                                                           @RequestParam(required = false) String conversationId) {
        // 管理 Agent 不接入用户画像
        return stream(adminAgentClient, AgentConfig.ADMIN_PERSONA, message, memoryKey("admin", conversationId));
    }

    /** 购物端非流式保底 */
    @PostMapping("/chat")
    public Result chat(@RequestBody ChatRequest body) {
        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            return Result.fail("消息不能为空", null);
        }
        String cid = memoryKey("shop", body.getConversationId());
        final Long uid = UserContext.getUserId();
        log.info("非流式对话 conversationId={} message={}", cid, message);
        try {
            String answer = shopAgentClient.prompt()
                    .toolContext(identityContext())
                    .system(AgentConfig.SHOP_PERSONA + profileService.buildProfileBlock(uid))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                    .user(message)
                    .call()
                    .content();
            profileService.extractAsync(uid, message, answer == null ? "" : answer);
            return Result.success("成功", Map.of("conversationId", cid,
                    "answer", answer == null ? "" : answer));
        } catch (Exception e) {
            log.error("非流式对话异常 conversationId={}", cid, e);
            return Result.fail("AI 服务暂时不可用，请稍再试", null);
        }
    }

    /** 清空当前用户的会话记忆 */
    @PostMapping("/chat/reset")
    public Result reset(@RequestBody ChatRequest body) {
        chatMemory.clear(memoryKey("shop", body.getConversationId()));
        return Result.success("会话已重置");
    }

    @PostMapping("/agent/admin/reset")
    public Result adminReset(@RequestBody ChatRequest body) {
        chatMemory.clear(memoryKey("admin", body.getConversationId()));
        return Result.success("会话已重置");
    }

    // ---------------- 内部 ----------------

    /**
     * SSE 流式核心（P8 改造）：
     * - uid 必须在请求线程捕获（reactor 线程无 ThreadLocal）；doFinally 异步提炼画像
     * - doOnNext 聚合助手回复全文供画像提炼；doOnCancel 记客户端停止；doFinally 覆盖成功/取消/异常
     * - system 用 persona + 画像块覆盖式注入（request 级 .system() 会覆盖 defaultSystem，故 persona 必须一并传）
     */
    private Flux<ServerSentEvent<String>> stream(ChatClient agent, String persona, String message, String cid) {
        final Long uid = UserContext.getUserId();   // ⚠️ 请求线程捕获，闭包内禁用 UserContext
        final StringBuilder buf = new StringBuilder();
        final String profileBlock = profileService.buildProfileBlock(uid);
        log.info("[Agent] SSE 对话 cid={} message={} userId={} profileInj={}",
                cid, message, uid, profileBlock.isEmpty() ? "n" : "y");
        return agent.prompt()
                .toolContext(identityContext())
                .system(persona + profileBlock)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .user(message)
                .stream()
                .content()
                .doOnNext(token -> buf.append(token))
                .map(token -> ServerSentEvent.builder(token).event("content").build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder(DONE_SIGNAL).event("done").build()))
                .onErrorResume(e -> {
                    log.error("[Agent] SSE 流式对话异常 cid={}", cid, e);
                    return Flux.just(
                            ServerSentEvent.<String>builder("抱歉，开小差了，请稍后再试～").event("content").build(),
                            ServerSentEvent.<String>builder(DONE_SIGNAL).event("done").build());
                })
                .doOnCancel(() -> log.info("[Agent] 客户端停止生成 cid={}", cid))
                .doFinally(signal -> profileService.extractAsync(uid, message, buf.toString()));
    }

    /** 身份上下文：写入 toolContext 供工具取用（模型不可见） */
    private Map<String, Object> identityContext() {
        UserContext.Principal p = UserContext.get();
        Map<String, Object> ctx = new HashMap<>();
        if (p != null) {
            ctx.put("userId", p.userId());
            ctx.put("username", p.username());
            ctx.put("role", p.role());
        } else {
            ctx.put("userId", "anonymous");
        }
        return ctx;
    }

    /** 会话记忆键：按用户隔离（未登录用匿名会话） */
    private String memoryKey(String agent, String conversationId) {
        String cid = (conversationId == null || conversationId.isBlank()) ? "default" : conversationId.trim();
        Long uid = UserContext.getUserId();
        return agent + ":" + (uid == null ? "anon" : "u" + uid) + ":" + cid;
    }

    @Data
    public static class ChatRequest {
        private String message;
        private String conversationId;
    }
}