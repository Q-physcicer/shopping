package com.shop.chat.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 轻量用户画像（P8 长期记忆）：
 * - 存储：Redis Hash chat:profile:u{userId}，字段 preferredCategories/interestedProducts/styleNote，TTL 30 天
 * - 提炼：每轮流结束后 doFinally 异步触发一次 DeepSeek 调用（temperature 0.2），把本轮对话里的稳定偏好合并进已有画像
 * - 注入：AiChatController 把画像拼进 system prompt（模型可见），让小智跨会话记住用户偏好
 * - 与会话窗口记忆（RedisChatMemoryRepository）正交：那个记当前会话上下文，这个记跨会话的用户画像
 *
 * 设计取舍：画像只从 Redis 本地读（快），最近订单等实时数据不注入——交由 Agent 工具按需查，避免每轮多一次 Feign。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private static final String PREFIX = "chat:profile:u";
    private static final Duration TTL = Duration.ofDays(30);
    /** 用户消息短于该长度不提炼（"是的/确认"等无偏好信号） */
    private static final int MIN_MSG_LEN = 10;

    private final StringRedisTemplate redis;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTRACT_PROMPT = """
            你是用户画像提炼器。根据【已有画像】和【本轮对话】，输出合并后的最新用户画像。
            严格输出一行 JSON，不要任何解释、不要 markdown 代码围栏：
            {"preferredCategories":"逗号分隔,最多5个中文品类,无新信号则保留旧值,无则空串",
             "interestedProducts":"逗号分隔,最多5个具体商品/型号/预算线索,无新信号则保留旧值,无则空串",
             "styleNote":"50字以内互动风格与诉求备注,无新信号则保留旧值,无则空串"}
            规则：只提炼稳定偏好（品类/预算/风格），不记一次性购买需求；没有新偏好信号就原样保留已有画像字段；任何字段无法判断则空串。
            【已有画像】%s
            【用户】%s
            【助手】%s
            """;

    /**
     * 构造画像注入块（拼到 system prompt）。匿名/无画像/异常一律返回空串，不影响对话。
     */
    public String buildProfileBlock(Long uid) {
        if (uid == null) {
            return "";
        }
        try {
            Map<Object, Object> profile = redis.opsForHash().entries(key(uid));
            if (profile == null || profile.isEmpty()) {
                return "";
            }
            String cate = str(profile.get("preferredCategories"));
            String prod = str(profile.get("interestedProducts"));
            String style = str(profile.get("styleNote"));
            if ((cate == null || cate.isBlank()) && (prod == null || prod.isBlank()) && (style == null || style.isBlank())) {
                return "";
            }
            StringBuilder sb = new StringBuilder("\n\n【用户画像（内部长期记忆，仅供个性化服务，不要逐字复述给用户）】\n");
            if (cate != null && !cate.isBlank()) sb.append("- 偏好品类：").append(cate).append("\n");
            if (prod != null && !prod.isBlank()) sb.append("- 关注商品/预算：").append(prod).append("\n");
            if (style != null && !style.isBlank()) sb.append("- 互动风格：").append(style).append("\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("[UserProfile] 读取画像失败 uid={}：{}", uid, e.getMessage());
            return "";
        }
    }

    /**
     * 异步提炼并合并画像。在 SSE 流 doFinally 中调用——跑在 reactor 线程，
     * 切到 boundedElastic 执行；任何异常吞掉仅记日志，绝不影响对话主链路。
     */
    public void extractAsync(Long uid, String userMsg, String assistantMsg) {
        if (uid == null || userMsg == null || userMsg.length() < MIN_MSG_LEN) {
            return;
        }
        Mono.fromRunnable(() -> doExtract(uid, userMsg, assistantMsg))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        __ -> {},
                        e -> log.warn("[UserProfile] 提炼异常 uid={}：{}", uid, e.getMessage())
                );
    }

    private void doExtract(Long uid, String userMsg, String assistantMsg) {
        try {
            Map<Object, Object> old = redis.opsForHash().entries(key(uid));
            String oldJson = (old == null || old.isEmpty()) ? "{}" : objectMapper.writeValueAsString(old);

            String prompt = String.format(EXTRACT_PROMPT, oldJson, truncate(userMsg, 500), truncate(assistantMsg, 800));
            String raw = chatModel.call(new Prompt(
                    List.of(new UserMessage(prompt)),
                    OpenAiChatOptions.builder().temperature(0.2).build()
            )).getResult().getOutput().getText();

            if (raw == null || raw.isBlank()) {
                return;
            }
            String json = stripFence(raw);
            JsonNode node = objectMapper.readTree(json);
            putIfPresent(uid, "preferredCategories", node.path("preferredCategories").asText(""));
            putIfPresent(uid, "interestedProducts", node.path("interestedProducts").asText(""));
            putIfPresent(uid, "styleNote", node.path("styleNote").asText(""));
            redis.expire(key(uid), TTL);
            log.info("[UserProfile] 画像已更新 uid={}", uid);
        } catch (Exception e) {
            log.warn("[UserProfile] 提炼失败 uid={}：{}", uid, e.getMessage());
        }
    }

    private void putIfPresent(Long uid, String field, String value) {
        if (value != null && !value.isBlank()) {
            // P2：截 200 字入库（这些文本最终拼进 system prompt；不设限会被恶意对话注入超长内容）
            String trimmed = value.trim();
            if (trimmed.length() > 200) {
                trimmed = trimmed.substring(0, 200);
            }
            redis.opsForHash().put(key(uid), field, trimmed);
            // 每次写入续期，防画像提前过期
            redis.expire(key(uid), java.time.Duration.ofDays(30));
        }
    }

    /** 剥除模型可能附加的 ```json ... ``` 围栏 */
    private String stripFence(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String key(Long uid) {
        return PREFIX + uid;
    }
}