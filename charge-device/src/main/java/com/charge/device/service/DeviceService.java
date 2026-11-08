package com.charge.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charge.common.exception.BizException;
import com.charge.common.security.JwtUtil;
import com.charge.common.security.TokenConstants;
import com.charge.device.entity.Device;
import com.charge.device.feign.AlarmClient;
import com.charge.device.feign.AlarmClient.AlarmCreateReq;
import com.charge.device.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    /** 设备详情缓存过期时间（秒） */
    private static final long CACHE_TTL = 60;

    private final DeviceMapper deviceMapper;
    private final StringRedisTemplate redisTemplate;
    private final AlarmClient alarmClient;
    private final JwtUtil jwtUtil;

    @Value("${charge.internal.secret}")
    private String internalSecret;

    public DeviceService(DeviceMapper deviceMapper, StringRedisTemplate redisTemplate,
                         AlarmClient alarmClient, JwtUtil jwtUtil) {
        this.deviceMapper = deviceMapper;
        this.redisTemplate = redisTemplate;
        this.alarmClient = alarmClient;
        this.jwtUtil = jwtUtil;
    }

    /** 分页查询（自动拼租户条件） */
    public Page<Device> page(Long tenantId, int page, int size,
                             String status, String station, String keyword) {
        QueryWrapper<Device> qw = new QueryWrapper<Device>()
                .eq("tenant_id", tenantId)
                .eq(status != null && !status.isBlank(), "status", status)
                .eq(station != null && !station.isBlank(), "station", station)
                .orderByDesc("update_time");
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword + "%";
            qw.and(w -> w.like("code", kw).or().like("name", kw));
        }
        return deviceMapper.selectPage(new Page<>(page, size), qw);
    }

    /** 按 id 查询（租户隔离 + Redis 缓存） */
    public Device getById(Long tenantId, Long id) {
        Device d = deviceMapper.selectById(id);
        checkTenant(tenantId, d);
        return d;
    }

    /**
     * 按编码查询：先查 Redis 缓存（60 秒），未命中回源数据库并回填
     * —— 热点设备（如大屏轮询展示）的典型缓存用法
     */
    public Device getByCode(Long tenantId, String code) {
        String key = cacheKey(tenantId, code);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return fromJson(cached);
        }
        Device d = deviceMapper.selectOne(new QueryWrapper<Device>()
                .eq("tenant_id", tenantId).eq("code", code));
        if (d == null) {
            throw new BizException(404, "设备不存在：" + code);
        }
        redisTemplate.opsForValue().set(key, toJson(d), CACHE_TTL, TimeUnit.SECONDS);
        return d;
    }

    public void create(Long tenantId, Device d) {
        if (d.getCode() == null || d.getCode().isBlank()) {
            throw new BizException("设备编码不能为空");
        }
        Long exists = deviceMapper.selectCount(new QueryWrapper<Device>()
                .eq("tenant_id", tenantId).eq("code", d.getCode()));
        if (exists != null && exists > 0) {
            throw new BizException("设备编码已存在：" + d.getCode());
        }
        d.setId(null);
        d.setTenantId(tenantId);
        d.setStatus(d.getStatus() == null || d.getStatus().isBlank() ? Device.STATUS_IDLE : d.getStatus());
        LocalDateTime now = LocalDateTime.now();
        d.setCreateTime(now);
        d.setUpdateTime(now);
        deviceMapper.insert(d);
    }

    public void update(Long tenantId, Long id, Device d) {
        Device db = deviceMapper.selectById(id);
        checkTenant(tenantId, db);
        d.setId(id);
        d.setTenantId(tenantId);
        d.setUpdateTime(LocalDateTime.now());
        deviceMapper.updateById(d);
        evict(tenantId, db.getCode());
    }

    public void delete(Long tenantId, Long id) {
        Device db = deviceMapper.selectById(id);
        checkTenant(tenantId, db);
        deviceMapper.deleteById(id);
        evict(tenantId, db.getCode());
    }

    /**
     * 状态上报：更新状态并清理缓存；
     * 若变为故障，通过 OpenFeign 跨服务创建告警记录（失败不阻断主流程）
     */
    public void reportStatus(Long tenantId, Long id, String status) {
        Set<String> allowed = Set.of(Device.STATUS_IDLE, Device.STATUS_CHARGING,
                Device.STATUS_FAULT, Device.STATUS_OFFLINE);
        if (!allowed.contains(status)) {
            throw new BizException("非法状态：" + status);
        }
        Device db = deviceMapper.selectById(id);
        checkTenant(tenantId, db);

        Device upd = new Device();
        upd.setId(id);
        upd.setStatus(status);
        upd.setUpdateTime(LocalDateTime.now());
        deviceMapper.updateById(upd);
        evict(tenantId, db.getCode());

        // 故障联动：Feign 调用告警服务
        if (Device.STATUS_FAULT.equals(status)) {
            try {
                String token = jwtUtil.generateToken(0L, tenantId, "SERVICE",
                        TokenConstants.TYPE_SERVICE, 3600);
                alarmClient.createAlarm("Bearer " + token, new AlarmCreateReq(
                        tenantId, db.getCode(), db.getName(), db.getStation(),
                        "CRITICAL", "设备故障告警：" + db.getName() + "（" + db.getCode() + "）上报故障"));
            } catch (Exception e) {
                // 告警服务不可用时不能影响设备状态上报，记录日志降级处理
                log.warn("创建告警失败（设备状态已更新）: {}", e.getMessage());
            }
        }
    }

    private void checkTenant(Long tenantId, Device d) {
        if (d == null) {
            throw new BizException(404, "设备不存在");
        }
        if (!tenantId.equals(d.getTenantId())) {
            // 不区分"不存在"与"无权限"，避免越权探测
            throw new BizException(404, "设备不存在");
        }
    }

    private String cacheKey(Long tenantId, String code) {
        return "charge:device:" + tenantId + ":" + code;
    }

    private void evict(Long tenantId, String code) {
        redisTemplate.delete(cacheKey(tenantId, code));
    }

    private String toJson(Device d) {
        return String.format(
                "{\"id\":%d,\"tenantId\":%d,\"code\":\"%s\",\"name\":\"%s\",\"station\":\"%s\","
                        + "\"powerKw\":%s,\"status\":\"%s\"}",
                d.getId(), d.getTenantId(), d.getCode(), d.getName(), d.getStation(),
                d.getPowerKw(), d.getStatus());
    }

    private Device fromJson(String json) {
        // 轻量解析：缓存结构简单，避免引入额外序列化依赖
        Device d = new Device();
        d.setTenantId(Long.valueOf(find(json, "tenantId")));
        d.setCode(find(json, "code"));
        d.setName(find(json, "name"));
        d.setStation(find(json, "station"));
        d.setStatus(find(json, "status"));
        return d;
    }

    private String find(String json, String field) {
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) {
            key = "\"" + field + "\":";
            i = json.indexOf(key);
            if (i < 0) {
                return null;
            }
            int start = i + key.length();
            int end = json.indexOf(',', start);
            return json.substring(start, end < 0 ? json.length() - 1 : end).trim();
        }
        int start = i + key.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
