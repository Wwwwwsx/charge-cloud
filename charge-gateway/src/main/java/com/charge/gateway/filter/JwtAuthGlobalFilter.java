package com.charge.gateway.filter;

import com.charge.common.result.CommonResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 全局 JWT 鉴权：
 * 白名单（登录/注册/健康检查）直接放行，其余请求必须携带有效 token。
 * 校验失败返回 401 统一 JSON。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    /** 白名单：不需要登录即可访问 */
    private static final Set<String> WHITE_LIST = Set.of(
            "/auth/login",
            "/auth/register",
            "/auth/health",
            "/ai/health"
    );

    private final com.charge.common.security.JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthGlobalFilter(com.charge.common.security.JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 预检请求与白名单放行
        if (request.getMethod() == org.springframework.http.HttpMethod.OPTIONS
                || isWhite(path)) {
            return chain.filter(exchange);
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        try {
            jwtUtil.parse(com.charge.common.security.JwtUtil.stripBearer(auth));
            return chain.filter(exchange);
        } catch (Exception e) {
            return writeUnauthorized(exchange.getResponse(), e.getMessage());
        }
    }

    private boolean isWhite(String path) {
        if (WHITE_LIST.contains(path)) {
            return true;
        }
        // 服务间内部接口（X-Internal-Secret 保护）不过网关，放行仅作兜底
        return path.startsWith("/auth/internal/");
    }

    private Mono<Void> writeUnauthorized(ServerHttpResponse response, String msg) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(CommonResult.fail(401, msg == null ? "未登录或登录已失效" : msg));
        } catch (JsonProcessingException e) {
            body = "{\"code\":401,\"msg\":\"未登录\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 在路由与限流之前完成鉴权
        return -100;
    }
}
