package com.charge.auth.dto;

/**
 * 认证接口入参/出参（record 简化 POJO）
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterReq(String username, String password, String nickname) {
    }

    public record LoginReq(String username, String password) {
    }

    public record TokenResp(String token, Long userId, Long tenantId, String username, String role) {
    }
}
