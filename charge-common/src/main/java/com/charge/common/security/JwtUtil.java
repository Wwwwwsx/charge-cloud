package com.charge.common.security;

import com.charge.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发与校验。
 * 各服务通过 charge.jwt.secret 配置同一把密钥（生产上应放到 Nacos 配置中心统一管理）。
 */
@Component
public class JwtUtil {

    @Value("${charge.jwt.secret}")
    private String secret;

    private SecretKey key() {
        // HMAC-SHA256 要求密钥至少 32 字节
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token
     *
     * @param uid        用户 id（服务间 token 传 0）
     * @param tenantId   租户 id
     * @param role       角色
     * @param type       user=用户 token，service=服务间 token
     * @param ttlSeconds 有效期（秒）
     */
    public String generateToken(Long uid, Long tenantId, String role, String type, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(uid))
                .claims().add(TokenConstants.CLAIM_TENANT, tenantId)
                .add(TokenConstants.CLAIM_ROLE, role)
                .add(TokenConstants.CLAIM_TYPE, type)
                .and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key())
                .compact();
    }

    /**
     * 解析 token，非法/过期抛 BizException(401)
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
    }

    /**
     * 从 Authorization 头（可带 Bearer 前缀）解析租户 id
     */
    public Long tenantOf(String authorizationHeader) {
        return parse(stripBearer(authorizationHeader)).get(TokenConstants.CLAIM_TENANT, Long.class);
    }

    /**
     * 从 Authorization 头解析用户 id
     */
    public Long uidOf(String authorizationHeader) {
        return Long.parseLong(parse(stripBearer(authorizationHeader)).getSubject());
    }

    public static String stripBearer(String header) {
        if (header == null || header.isBlank()) {
            throw new BizException(401, "缺少登录凭证");
        }
        return header.startsWith(TokenConstants.BEARER) ? header.substring(7) : header;
    }

    public Map<String, Object> claimsOf(String authorizationHeader) {
        Claims c = parse(stripBearer(authorizationHeader));
        return Map.of(
                "uid", c.getSubject(),
                TokenConstants.CLAIM_TENANT, c.get(TokenConstants.CLAIM_TENANT),
                TokenConstants.CLAIM_ROLE, c.get(TokenConstants.CLAIM_ROLE),
                TokenConstants.CLAIM_TYPE, c.get(TokenConstants.CLAIM_TYPE)
        );
    }
}
