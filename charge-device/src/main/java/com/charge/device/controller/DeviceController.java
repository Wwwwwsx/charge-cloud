package com.charge.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charge.common.result.CommonResult;
import com.charge.common.security.JwtUtil;
import com.charge.device.entity.Device;
import com.charge.device.service.DeviceService;
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

/**
 * 充电桩设备接口。
 * 所有接口从 JWT 解析租户身份，实现多租户数据隔离。
 */
@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final JwtUtil jwtUtil;

    public DeviceController(DeviceService deviceService, JwtUtil jwtUtil) {
        this.deviceService = deviceService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public CommonResult<Page<Device>> page(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String keyword) {
        Long tid = jwtUtil.tenantOf(auth);
        return CommonResult.ok(deviceService.page(tid, page, size, status, station, keyword));
    }

    @GetMapping("/{id}")
    public CommonResult<Device> getById(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        return CommonResult.ok(deviceService.getById(jwtUtil.tenantOf(auth), id));
    }

    @GetMapping("/code/{code}")
    public CommonResult<Device> getByCode(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String code) {
        return CommonResult.ok(deviceService.getByCode(jwtUtil.tenantOf(auth), code));
    }

    @PostMapping
    public CommonResult<Void> create(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Device device) {
        deviceService.create(jwtUtil.tenantOf(auth), device);
        return CommonResult.ok();
    }

    @PutMapping("/{id}")
    public CommonResult<Void> update(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id, @RequestBody Device device) {
        deviceService.update(jwtUtil.tenantOf(auth), id, device);
        return CommonResult.ok();
    }

    @DeleteMapping("/{id}")
    public CommonResult<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        deviceService.delete(jwtUtil.tenantOf(auth), id);
        return CommonResult.ok();
    }

    /**
     * 状态上报（模拟设备侧心跳/上报）：IDLE / CHARGING / FAULT / OFFLINE
     */
    @PostMapping("/{id}/status")
    public CommonResult<Void> reportStatus(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id, @RequestParam String status) {
        deviceService.reportStatus(jwtUtil.tenantOf(auth), id, status);
        return CommonResult.ok();
    }
}
