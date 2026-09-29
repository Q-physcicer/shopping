package com.shop.common.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关注入的 header 解析用户身份（信任边界：网关已鉴权并剥除伪造 header）。
 * 由 UserContextWebConfig 自动注册到所有 HandlerMapping。
 */
public class UserContextInterceptor implements HandlerInterceptor {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uid = request.getHeader(HEADER_USER_ID);
        if (uid != null && !uid.isBlank()) {
            try {
                UserContext.set(new UserContext.Principal(
                        Long.valueOf(uid),
                        request.getHeader(HEADER_USER_NAME),
                        request.getHeader(HEADER_USER_ROLE) == null ? "USER" : request.getHeader(HEADER_USER_ROLE)));
            } catch (NumberFormatException e) {
                // 畸形身份 header：拒绝该请求而非 500（信任边界内防御）
                response.setStatus(401);
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}