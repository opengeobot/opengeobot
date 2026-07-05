/*
 * Function: Cloud control Spring Boot main application — modular monolith entry point
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the OpenGeoBot cloud control plane.
 * Scans the entire {@code io.opengeobot} package tree so that all domain
 * modules (platform-common, platform-iam, platform-governance, …) contribute
 * their configurations and beans to the single application context.
 * {@link EnableScheduling} activates the scheduled-task support required by
 * the recovery backup job (F-RECOVERY-001).
 */
@SpringBootApplication(scanBasePackages = "io.opengeobot")
@MapperScan("io.opengeobot.platform.**.repository")
@EnableScheduling
public class CloudControlApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudControlApplication.class, args);
    }
}
