package com.shop.common.context;

/**
 * 用户身份上下文（ThreadLocal）。
 * 网关 AuthGlobalFilter 校验 JWT 后注入 X-User-Id / X-User-Name / X-User-Role header，
 * 各业务服务的 UserContextInterceptor 把 header 解析进 ThreadLocal，
 * Controller/Service 一律 UserContext.getUserId() 取身份，禁止路径传 userId。
 */
public class UserContext {

    public record Principal(Long userId, String username, String role) {
        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    public static void set(Principal principal) {
        HOLDER.set(principal);
    }

    /** @return 当前登录用户（net） */
    public static Principal get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        Principal p = HOLDER.get();
        return p == null ? null : p.userId();
    }

    public static String getRole() {
        Principal p = HOLDER.get();
        return p == null ? null : p.role();
    }

    /** 匿名请求（白名单接口），返回 null */
    public static boolean isAnonymous() {
        return HOLDER.get() == null;
    }

    /** 请求结束必须清理，防止容器线程复用导致的串号（见 UserContextInterceptor#afterCompletion） */
    public static void clear() {
        HOLDER.remove();
    }
}