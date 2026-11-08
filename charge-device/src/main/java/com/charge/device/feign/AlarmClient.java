package com.charge.device.feign;

import com.charge.common.result.CommonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 告警服务的 Feign 客户端：
 * 设备状态变为"故障"时，跨服务创建一条告警记录。
 * 认证方式：调用方本地签发服务间 JWT，通过 Authorization 头传递。
 */
@FeignClient(name = "charge-alarm", path = "/alarms")
public interface AlarmClient {

    @PostMapping("/internal")
    CommonResult<Void> createAlarm(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody AlarmCreateReq req);

    /**
     * 内部创建告警入参
     */
    record AlarmCreateReq(Long tenantId, String deviceCode, String deviceName,
                          String station, String level, String content) {
    }
}
