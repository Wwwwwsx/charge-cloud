package com.charge.alarm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charge.alarm.entity.AlarmRecord;
import com.charge.alarm.entity.AlarmRule;
import com.charge.alarm.service.AlarmService;
import com.charge.common.result.CommonResult;
import com.charge.common.security.JwtUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 告警接口：规则管理 + 记录管理 + 统计
 */
@RestController
public class AlarmControllers {

    private final AlarmService alarmService;
    private final JwtUtil jwtUtil;

    public AlarmControllers(AlarmService alarmService, JwtUtil jwtUtil) {
        this.alarmService = alarmService;
        this.jwtUtil = jwtUtil;
    }

    // ===== 告警规则 =====

    @GetMapping("/alarm-rules")
    public CommonResult<List<AlarmRule>> rules(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return CommonResult.ok(alarmService.listRules(jwtUtil.tenantOf(auth)));
    }

    @PostMapping("/alarm-rules")
    public CommonResult<Void> createRule(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody AlarmRule rule) {
        alarmService.createRule(jwtUtil.tenantOf(auth), rule);
        return CommonResult.ok();
    }

    @PutMapping("/alarm-rules/{id}")
    public CommonResult<Void> updateRule(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id, @RequestBody AlarmRule rule) {
        alarmService.updateRule(jwtUtil.tenantOf(auth), id, rule);
        return CommonResult.ok();
    }

    @DeleteMapping("/alarm-rules/{id}")
    public CommonResult<Void> deleteRule(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        alarmService.deleteRule(jwtUtil.tenantOf(auth), id);
        return CommonResult.ok();
    }

    // ===== 告警记录 =====

    @GetMapping("/alarms")
    public CommonResult<Page<AlarmRecord>> alarms(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status) {
        return CommonResult.ok(alarmService.pageRecords(jwtUtil.tenantOf(auth), page, size, level, status));
    }

    @PutMapping("/alarms/{id}/handle")
    public CommonResult<Void> handle(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        alarmService.handle(jwtUtil.tenantOf(auth), id);
        return CommonResult.ok();
    }

    /**
     * 内部接口：设备服务 Feign 写入故障告警（服务间 JWT 保护）
     */
    @PostMapping("/alarms/internal")
    public CommonResult<Void> createInternal(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody AlarmRecord record) {
        alarmService.createFromService(auth, record);
        return CommonResult.ok();
    }

    // ===== 统计 =====

    @GetMapping("/alarms/stats")
    public CommonResult<Map<String, Object>> stats(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return CommonResult.ok(alarmService.stats(jwtUtil.tenantOf(auth)));
    }
}
