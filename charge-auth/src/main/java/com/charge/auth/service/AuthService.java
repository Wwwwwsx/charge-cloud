package com.charge.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charge.auth.dto.AuthDtos.LoginReq;
import com.charge.auth.dto.AuthDtos.RegisterReq;
import com.charge.auth.dto.AuthDtos.TokenResp;
import com.charge.auth.entity.SysUser;
import com.charge.auth.mapper.SysUserMapper;
import com.charge.common.exception.BizException;
import com.charge.common.security.JwtUtil;
import com.charge.common.security.TokenConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    /** 用户 token 有效期：24 小时 */
    private static final long USER_TTL = 24 * 3600L;
    /** 服务间 token 有效期：7 天 */
    private static final long SERVICE_TTL = 7 * 24 * 3600L;

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${charge.internal.secret}")
    private String internalSecret;

    public AuthService(SysUserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    public void register(RegisterReq req) {
        if (req.username() == null || req.username().length() < 3) {
            throw new BizException("用户名至少 3 个字符");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        Long exists = userMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", req.username()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已被注册");
        }
        SysUser u = new SysUser();
        u.setTenantId(1L); // 新用户默认进租户1，演示够用
        u.setUsername(req.username());
        u.setPassword(encoder.encode(req.password()));
        u.setNickname(req.nickname() == null || req.nickname().isBlank() ? req.username() : req.nickname());
        u.setRole("USER");
        u.setCreateTime(LocalDateTime.now());
        userMapper.insert(u);
    }

    public TokenResp login(LoginReq req) {
        SysUser u = userMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", req.username()));
        if (u == null || !encoder.matches(req.password(), u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(
                u.getId(), u.getTenantId(), u.getRole(), TokenConstants.TYPE_USER, USER_TTL);
        return new TokenResp(token, u.getId(), u.getTenantId(), u.getUsername(), u.getRole());
    }

    /**
     * 签发服务间 token（供 charge-ai 等内部服务调用业务接口），
     * 由 X-Internal-Secret 请求头保护，不对外暴露。
     */
    public String serviceToken(String providedSecret) {
        if (providedSecret == null || !internalSecret.equals(providedSecret)) {
            throw new BizException(403, "内部密钥错误");
        }
        return jwtUtil.generateToken(0L, 1L, "SERVICE", TokenConstants.TYPE_SERVICE, SERVICE_TTL);
    }
}
