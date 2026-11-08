package com.charge.alarm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 告警服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.charge")
@MapperScan("com.charge.alarm.mapper")
public class AlarmApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlarmApplication.class, args);
    }
}
