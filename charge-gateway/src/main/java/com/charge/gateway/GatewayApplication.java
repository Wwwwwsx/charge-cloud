package com.charge.gateway;

import com.charge.common.security.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 网关启动类。
 * 扫描 com.charge 以复用 common 中的 JwtUtil（全局异常处理器不生效于网关，网关无 Controller）。
 */
@SpringBootApplication(scanBasePackages = "com.charge")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * 限流维度：登录用户按 userId，未登录按客户端 IP
     * —— 防止单用户刷接口，也防止匿名流量打垮服务
     */
    @Bean
    public KeyResolver apiKeyResolver(JwtUtil jwtUtil) {
        return exchange -> {
            String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    Long uid = jwtUtil.uidOf(auth);
                    return Mono.just("u:" + uid);
                } catch (Exception ignore) {
                    // token 无效的交给鉴权过滤器处理，这里退化为按 IP 限流
                }
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote != null ? remote.getAddress().getHostAddress() : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
