package com.charge.device;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 设备服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.charge")
@MapperScan("com.charge.device.mapper")
@EnableFeignClients(basePackages = "com.charge.device.feign")
public class DeviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceApplication.class, args);
    }
}
