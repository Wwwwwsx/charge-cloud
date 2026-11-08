package com.charge.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charge.auth.entity.SysUser;
import com.charge.auth.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

/**
 * 认证服务启动类
 * 扫描 com.charge：既包含本服务，也包含 charge-common 中的组件（JwtUtil、全局异常）
 */
@SpringBootApplication(scanBasePackages = "com.charge")
@MapperScan("com.charge.auth.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    /**
     * 首次启动时初始化演示账号（幂等：表里有人就不插）
     * admin/admin123   租户1（星星充电运营）
     * operator2/operator123  租户2（另一家运营商）
     */
    @Bean
    CommandLineRunner initUsers(SysUserMapper userMapper) {
        return args -> {
            Long count = userMapper.selectCount(new QueryWrapper<>());
            if (count != null && count > 0) {
                return;
            }
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            userMapper.insert(user(1L, "admin", encoder.encode("admin123"), "超级管理员", "ADMIN"));
            userMapper.insert(user(1L, "operator1", encoder.encode("op123456"), "运营人员", "USER"));
            userMapper.insert(user(2L, "operator2", encoder.encode("operator123"), "租户2管理员", "ADMIN"));
        };
    }

    private SysUser user(Long tenantId, String username, String password, String nickname, String role) {
        SysUser u = new SysUser();
        u.setTenantId(tenantId);
        u.setUsername(username);
        u.setPassword(password);
        u.setNickname(nickname);
        u.setRole(role);
        u.setCreateTime(LocalDateTime.now());
        return u;
    }
}
