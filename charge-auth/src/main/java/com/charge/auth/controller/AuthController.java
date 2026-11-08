package com.charge.auth.controller;

import com.charge.auth.dto.AuthDtos.LoginReq;
import com.charge.auth.dto.AuthDtos.RegisterReq;
import com.charge.auth.dto.AuthDtos.TokenResp;
import com.charge.auth.service.AuthService;
import com.charge.common.result.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public CommonResult<Void> register(@RequestBody RegisterReq req) {
        authService.register(req);
        return CommonResult.ok();
    }

    @PostMapping("/login")
    public CommonResult<TokenResp> login(@RequestBody LoginReq req) {
        return CommonResult.ok(authService.login(req));
    }

    /**
     * 服务间 token 签发（内部接口，由内部密钥保护）
     */
    @PostMapping("/internal/token")
    public CommonResult<String> serviceToken(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        return CommonResult.ok(authService.serviceToken(secret));
    }

    /** 健康检查（网关白名单） */
    @GetMapping("/health")
    public CommonResult<String> health() {
        return CommonResult.ok("auth-up");
    }
}
