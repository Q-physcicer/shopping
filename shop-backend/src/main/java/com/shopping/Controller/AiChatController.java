package com.shopping.Controller;

import com.shopping.util.Result;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * AI 聊天客服接口
 * GET  /chat/stream  SSE 流式输出（content 事件逐包下发，[DONE] 事件结束）
 * POST /chat        非流式问答（保底/对照用）
 * 通过 conversationId 维护多轮会话上下文（内存版，最近 10 轮）
 */
@RestController
@RequestMapping("/chat")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    /** 每个会话保留的最大消息条数（一问一答算 2 条，即最近 10 轮） */
    private static final int MEMORY_WINDOW = 20;

    private static final String DONE_SIGNAL = "[DONE]";

    private final ChatClient chatClient;

    /** 会话记忆：conversationId -> 历史消息（内存版，重启丢失；持久化见优化方案） */
    private final Map<String, ConcurrentLinkedDeque<Message>> conversations = new ConcurrentHashMap<>();

    public AiChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * SSE 流式对话。用 GET 便于前端直接使用 EventSource。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message,
                                                    @RequestParam(required = false) String conversationId) {
        String cid = normalizeConversationId(conversationId);
        List<Message> history = snapshotHistory(cid);
        StringBuilder answerBuffer = new StringBuilder();

        log.info("SSE 对话请求 conversationId={} message={}", cid, message);

        return chatClient.prompt()
                .messages(history)
                .user(message)
                .stream()
                .content()
                .map(token -> ServerSentEvent.builder(token).event("content").build())
                // 累积完整回答，流结束后写入会话记忆
                .doOnNext(event -> answerBuffer.append(event.data()))
                .concatWith(Flux.just(ServerSentEvent.<String>builder(DONE_SIGNAL).event("done").build()))
                .onErrorResume(e -> {
                    log.error("SSE 流式对话异常 conversationId={}", cid, e);
                    return Flux.just(
                            ServerSentEvent.<String>builder("抱歉，小智开小差了，请稍后再试～").event("content").build(),
                            ServerSentEvent.<String>builder(DONE_SIGNAL).event("done").build());
                })
                .doOnComplete(() -> remember(cid, new UserMessage(message), new AssistantMessage(answerBuffer.toString())));
    }

    /**
     * 非流式对话（保底/对照用）
     */
    @PostMapping
    public Result chat(@RequestBody ChatRequest body) {
        String message = body.getMessage();
        String cid = normalizeConversationId(body.getConversationId());
        if (message == null || message.isBlank()) {
            return Result.fail("消息不能为空", null);
        }
        log.info("非流式对话请求 conversationId={} message={}", cid, message);
        try {
            List<Message> history = snapshotHistory(cid);
            String answer = chatClient.prompt()
                    .messages(history)
                    .user(message)
                    .call()
                    .content();
            remember(cid, new UserMessage(message), new AssistantMessage(answer == null ? "" : answer));
            return Result.success("成功", Map.of("conversationId", cid, "answer", answer == null ? "" : answer));
        } catch (Exception e) {
            log.error("非流式对话异常 conversationId={}", cid, e);
            return Result.fail("AI 服务暂时不可用，请稍再试", null);
        }
    }

    /** 清空某个会话的上下文 */
    @PostMapping("/reset")
    public Result reset(@RequestBody ChatRequest body) {
        String cid = normalizeConversationId(body.getConversationId());
        conversations.remove(cid);
        return Result.success("会话已重置");
    }

    // ------------------------- 会话记忆（内存版） -------------------------

    private String normalizeConversationId(String cid) {
        if (cid == null || cid.isBlank()) {
            return "default";
        }
        return cid.trim();
    }

    /** 取当前历史快照（不修改原队列之外的引用，写入在 remember 中进行） */
    private List<Message> snapshotHistory(String cid) {
        ConcurrentLinkedDeque<Message> deque = conversations.get(cid);
        if (deque == null) {
            return List.of();
        }
        return new ArrayList<>(deque);
    }

    private void remember(String cid, UserMessage userMessage, AssistantMessage assistantMessage) {
        ConcurrentLinkedDeque<Message> deque =
                conversations.computeIfAbsent(cid, k -> new ConcurrentLinkedDeque<>());
        deque.offerLast(userMessage);
        deque.offerLast(assistantMessage);
        // 超出窗口则从最旧的开始丢弃
        while (deque.size() > MEMORY_WINDOW) {
            deque.pollFirst();
        }
    }

    @Data
    public static class ChatRequest {
        private String message;
        private String conversationId;
    }
}