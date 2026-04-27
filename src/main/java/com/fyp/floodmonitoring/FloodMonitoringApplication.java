package com.fyp.floodmonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FloodMonitoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(FloodMonitoringApplication.class, args);
    }
}
