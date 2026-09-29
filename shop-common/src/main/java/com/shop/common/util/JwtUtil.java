package com.shop.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具（HS256）
 * Payload: {sub=userId, username, role, jti, iat, exp}
 * Cookie 名 XM_TOKEN 保留，值为本工具签发的 JWT。
 * 条件装配：仅 classpath 有 jjwt 的模块（gateway/user/order 等服务）才会注册该 bean。
 */
@Component
@ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
public class JwtUtil {

    @Value("${shop.jwt.secret:dev-only-secret-change-in-prod-0123456789abcdef}")
    private String secret;

    @Value("${shop.jwt.expire-days:7}")
    private int expireDays;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // P2：默认 secret 是公开的仓库常量——未设 JWT_SECRET 等于任何人可伪造 ADMIN token
        if (DEFAULT_SECRET.equals(secret)) {
            org.slf4j.LoggerFactory.getLogger(JwtUtil.class)
                    .error("[JwtUtil] 正在使用默认开发密钥！生产必须设置环境变量 JWT_SECRET，否则 token 可被伪造");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static final String DEFAULT_SECRET = "dev-only-secret-change-in-prod-0123456789abcdef";

    /** 签发 JWT；userId 过期时间 7 天（可配） */
    public String generate(Long userId, String username, String role) {
        long now = System.currentTimeMillis();
        long exp = now + expireDays * 24L * 3600 * 1000;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .id(UUID.randomUUID().toString().replace("-", ""))   // jti：登出黑名单用
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 JWT。
     * @return Claims；签名无效/过期/格式错误时抛出 io.jsonwebtoken.JwtException 或 IllegalArgumentException
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}