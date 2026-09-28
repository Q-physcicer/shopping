package com.shop.chat.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话记忆 Redis 持久化（替代原内存 ConcurrentHashMap，服务重启不丢上下文）。
 * 结构：List chat:memory:{conversationId}，每条 = {"type":"user|assistant|system","text":"..."}，TTL 7 天。
 * 由 MessageWindowChatMemory 控制窗口（20 条 ≈ 10 轮），本类只做存取。
 */
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String PREFIX = "chat:memory:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> findConversationIds() {
        // 仅为满足接口；平台不提供全量会话枚举
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(@NonNull String conversationId) {
        List<String> raw = redis.opsForList().range(key(conversationId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        for (String json : raw) {
            Message m = fromJson(json);
            if (m != null) {
                messages.add(m);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(@NonNull String conversationId, List<Message> messages) {
        String k = key(conversationId);
        redis.delete(k);
        if (messages != null && !messages.isEmpty()) {
            List<String> json = new ArrayList<>(messages.size());
            for (Message m : messages) {
                json.add(toJson(m));
            }
            redis.opsForList().rightPushAll(k, json);
        }
        redis.expire(k, TTL);
    }

    @Override
    public void deleteByConversationId(@NonNull String conversationId) {
        redis.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return PREFIX + conversationId;
    }

    private String toJson(Message m) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("type", m.getMessageType().name());
        o.put("text", m.getText());
        return o.toString();
    }

    private Message fromJson(String json) {
        try {
            JsonNode o = objectMapper.readTree(json);
            String type = o.path("type").asText();
            String text = o.path("text").asText("");
            return MessageType.ASSISTANT.name().equals(type)
                    ? new AssistantMessage(text)
                    : new UserMessage(text);
        } catch (Exception e) {
            return null;
        }
    }
}