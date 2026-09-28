package com.shop.gateway.filter;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.shop.common.util.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器（核心安全边界）：
 * 1. 白名单直接放行（登录/注册/用户名查重/公开只读 GET/健康检查/匿名 AI 客服）
 * 2. 其余请求从 Cookie XM_TOKEN 或 Authorization: Bearer 提取 JWT 并校验签名/有效期/登出黑名单
 * 3. 校验通过：先剥除客户端伪造的身份 header，再注入真实身份（X-User-Id/X-User-Name/X-User-Role）
 * 4. /admin/** 要求 ADMIN 角色
 * 5. 失败统一返回 HTTP 401/403 + Result JSON（HTTP 状态码语义，前端不再比对魔法字符串）
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String COOKIE_TOKEN = "XM_TOKEN";
    private static final String BLACKLIST_PREFIX = "auth:logout:";

    /** 完全放行（无需登录） */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/user/login", "/user/register", "/user/username/**",
            "/actuator/**"
    );

    /** 公开只读 GET：浏览态（无身份 header 注入，匿名可访问） */
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/category/**", "/product/**", "/productPicture/**", "/resources/**",
            "/seckill/product/**", "/chat/**"
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redis;

    @Autowired
    public AuthGlobalFilter(JwtUtil jwtUtil, ReactiveStringRedisTemplate redis) {
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // 1) 白名单
        if (isPublic(path) || (HttpMethod.GET.equals(method) && isPublicGet(path))) {
            // 携带合法 token 的匿名接口也注入身份（如 chat Agent 判断是否可代客下单）
            return passWithOptionalIdentity(exchange, chain);
        }

        // 2) 提取 token
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "请先登录");
        }

        // 3) JWT 校验（签名/有效期）
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        // 4) 登出黑名单
        String jti = claims.getId();
        return redis.hasKey(BLACKLIST_PREFIX + jti)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return reject(exchange, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
                    }
                    // 5) 角色校验（管理端接口与管理 Agent 通道都要求 ADMIN）
                    String role = (String) claims.get("role");
                    if ((path.startsWith("/admin") || path.startsWith("/agent/admin")) && !"ADMIN".equals(role)) {
                        return reject(exchange, HttpStatus.FORBIDDEN, "无管理员权限");
                    }
                    // 6) 注入身份（先剥伪造 header）
                    ServerHttpRequest mutated = stripClientIdentityHeaders(request).mutate()
                            .header("X-User-Id", claims.getSubject())
                            .header("X-User-Name", String.valueOf(claims.get("username")))
                            .header("X-User-Role", role)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    /** 公开接口：有合法 token 就注入身份，没有就匿名透传（同样必须剥伪造 header） */
    private Mono<Void> passWithOptionalIdentity(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            return chain.filter(exchange.mutate().request(stripClientIdentityHeaders(request)).build());
        }
        try {
            Claims claims = jwtUtil.parse(token);
            ServerHttpRequest mutated = stripClientIdentityHeaders(request).mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Name", String.valueOf(claims.get("username")))
                    .header("X-User-Role", (String) claims.get("role"))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            // 公开接口不拒绝无效 token，按匿名放行
            return chain.filter(exchange.mutate().request(stripClientIdentityHeaders(request)).build());
        }
    }

    private ServerHttpRequest stripClientIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Name");
                    headers.remove("X-User-Role");
                })
                .build();
    }

    private String extractToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(COOKIE_TOKEN);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> MATCHER.match(p, path));
    }

    private boolean isPublicGet(String path) {
        return PUBLIC_GET_PATHS.stream().anyMatch(p -> MATCHER.match(p, path));
    }

    /** 统一 401/403 JSON 应答 */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status.value() + ",\"msg\":\"" + msg + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 早于路由转发，晚于最高优先级系统 filter
    }
}