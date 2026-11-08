package com.charge.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 充电桩设备
 */
@TableName("device")
public class Device {

    public static final String STATUS_IDLE = "IDLE";         // 空闲
    public static final String STATUS_CHARGING = "CHARGING"; // 充电中
    public static final String STATUS_FAULT = "FAULT";       // 故障
    public static final String STATUS_OFFLINE = "OFFLINE";   // 离线

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 设备编码（租户内唯一，如 CHG-0001） */
    private String code;

    private String name;

    /** 所属站点 */
    private String station;

    /** 额定功率 kW */
    private Double powerKw;

    /** IDLE / CHARGING / FAULT / OFFLINE */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public Double getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(Double powerKw) {
        this.powerKw = powerKw;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
