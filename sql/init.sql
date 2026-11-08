-- =====================================================
-- charge-cloud 数据库初始化脚本
-- 使用：mysql -uroot -p123456 < sql/init.sql
-- 说明：演示账号由 charge-auth 启动时自动写入（BCrypt 哈希在 Java 侧生成）
-- =====================================================
USE charge_cloud;

DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS device;
DROP TABLE IF EXISTS alarm_rule;
DROP TABLE IF EXISTS alarm_record;

-- 系统用户（多租户）
CREATE TABLE sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL COMMENT '租户 id',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    nickname    VARCHAR(64)           DEFAULT NULL,
    role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN/USER',
    create_time DATETIME              DEFAULT NULL,
    UNIQUE KEY uk_username (username),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户';

-- 充电桩设备
CREATE TABLE device (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    code        VARCHAR(32)  NOT NULL COMMENT '设备编码',
    name        VARCHAR(64)  NOT NULL,
    station     VARCHAR(64)           DEFAULT NULL COMMENT '所属站点',
    power_kw    DOUBLE                DEFAULT NULL COMMENT '额定功率 kW',
    status      VARCHAR(16)  NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/CHARGING/FAULT/OFFLINE',
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_tenant (tenant_id),
    KEY idx_station (station)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='充电桩设备';

-- 告警规则
CREATE TABLE alarm_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    metric      VARCHAR(16)  NOT NULL COMMENT 'TEMP/PWR/SOC',
    operator    VARCHAR(4)   NOT NULL COMMENT 'GT/LT/EQ',
    threshold   DOUBLE       NOT NULL,
    level       VARCHAR(16)  NOT NULL COMMENT 'WARNING/CRITICAL',
    station     VARCHAR(64)           DEFAULT NULL COMMENT '限定站点，空=全部',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    create_time DATETIME              DEFAULT NULL,
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警规则';

-- 告警记录
CREATE TABLE alarm_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    device_code  VARCHAR(32)           DEFAULT NULL,
    device_name  VARCHAR(64)           DEFAULT NULL,
    station      VARCHAR(64)           DEFAULT NULL,
    level        VARCHAR(16)  NOT NULL COMMENT 'WARNING/CRITICAL',
    content      VARCHAR(255) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/HANDLED',
    create_time  DATETIME              DEFAULT NULL,
    handle_time  DATETIME              DEFAULT NULL,
    KEY idx_tenant_status (tenant_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警记录';

-- ==================== 演示数据 ====================

-- 租户1：星星充电运营（6 台设备）
INSERT INTO device (tenant_id, code, name, station, power_kw, status, create_time, update_time)
VALUES (1, 'CHG-0001', '直流快充桩-01', '星海站', 120, 'CHARGING', NOW(), NOW()),
       (1, 'CHG-0002', '直流快充桩-02', '星海站', 120, 'IDLE', NOW(), NOW()),
       (1, 'CHG-0003', '交流慢充桩-03', '星海站', 7, 'IDLE', NOW(), NOW()),
       (1, 'CHG-0004', '直流快充桩-04', '中山站', 60, 'FAULT', NOW(), NOW()),
       (1, 'CHG-0005', '直流快充桩-05', '中山站', 60, 'CHARGING', NOW(), NOW()),
       (1, 'CHG-0006', '交流慢充桩-06', '滨江站', 7, 'OFFLINE', NOW(), NOW());

-- 租户2：另一家运营商（2 台设备，验证多租户隔离）
INSERT INTO device (tenant_id, code, name, station, power_kw, status, create_time, update_time)
VALUES (2, 'T2-0001', '租户2快充桩-01', '临港站', 90, 'IDLE', NOW(), NOW()),
       (2, 'T2-0002', '租户2快充桩-02', '临港站', 90, 'CHARGING', NOW(), NOW());

-- 告警规则（租户1）
INSERT INTO alarm_rule (tenant_id, metric, operator, threshold, level, station, enabled, create_time)
VALUES (1, 'TEMP', 'GT', 65, 'CRITICAL', NULL, 1, NOW()),
       (1, 'PWR', 'GT', 110, 'WARNING', '星海站', 1, NOW()),
       (1, 'SOC', 'LT', 10, 'WARNING', NULL, 1, NOW());

-- 告警记录（租户1：3 条未处理 + 1 条已处理）
INSERT INTO alarm_record (tenant_id, device_code, device_name, station, level, content, status, create_time, handle_time)
VALUES (1, 'CHG-0004', '直流快充桩-04', '中山站', 'CRITICAL', '设备故障告警：直流快充桩-04（CHG-0004）上报故障', 'OPEN', NOW(), NULL),
       (1, 'CHG-0005', '直流快充桩-05', '中山站', 'WARNING', '枪口温度 68℃ 超过阈值 65℃', 'OPEN', NOW(), NULL),
       (1, 'CHG-0001', '直流快充桩-01', '星海站', 'WARNING', '实时功率 115kW 超过阈值 110kW', 'OPEN', NOW(), NULL),
       (1, 'CHG-0006', '交流慢充桩-06', '滨江站', 'WARNING', '设备离线超过 30 分钟', 'HANDLED', NOW(), NOW());

-- 告警记录（租户2：验证隔离，租户1不应看到这条）
INSERT INTO alarm_record (tenant_id, device_code, device_name, station, level, content, status, create_time, handle_time)
VALUES (2, 'T2-0001', '租户2快充桩-01', '临港站', 'CRITICAL', '租户2的告警，租户1不可见', 'OPEN', NOW(), NULL);

SELECT 'charge-cloud 初始化完成' AS msg;
