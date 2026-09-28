package com.shop.common.context;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Feign 身份透传拦截器：
 * 把网关注入到本服务的 X-User-Id/X-User-Name/X-User-Role（存于 UserContext ThreadLocal）
 * 继续传给下游服务，形成完整的调用链身份（Agent 代购车/下单等跨服务写操作依赖此身份）。
 *
 * 信任边界说明：X-User-* 一律来自网关鉴权（网关已剥客户端伪造值）；
 * 生产环境业务端口(8101-8106)不得对公网开放，仅网关与内网可访问，否则 header 信任模型失效。
 */
@Component
@ConditionalOnClass(RequestInterceptor.class)
public class FeignIdentityInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        UserContext.Principal p = UserContext.get();
        if (p == null) {
            return;   // 匿名调用（如外部 MCP 客户端未带用户身份）
        }
        template.header(UserContextInterceptor.HEADER_USER_ID, String.valueOf(p.userId()));
        template.header(UserContextInterceptor.HEADER_USER_NAME, p.username());
        template.header(UserContextInterceptor.HEADER_USER_ROLE, p.role());
    }
}