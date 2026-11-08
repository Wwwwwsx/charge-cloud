package com.charge.alarm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 告警规则：指标 + 比较符 + 阈值 → 触发级别
 * 例：TEMP > 65 → CRITICAL
 */
@TableName("alarm_rule")
public class AlarmRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 指标：TEMP 温度 / PWR 功率 / SOC 电量 */
    private String metric;

    /** 比较符：GT / LT / EQ */
    private String operator;

    private Double threshold;

    /** WARNING / CRITICAL */
    private String level;

    /** 限定站点，为空表示全部站点 */
    private String station;

    private Boolean enabled;

    private LocalDateTime createTime;

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

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
