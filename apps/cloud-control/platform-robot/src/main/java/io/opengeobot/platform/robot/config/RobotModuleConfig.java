/*
 * Function: Robot module configuration — MyBatis-Plus type handler registration
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the platform robot module. Mapper scanning is handled
 * centrally by {@code CloudControlApplication.@MapperScan("io.opengeobot.platform.**.repository")}.
 * The {@link JsonbStringTypeHandler} is discovered automatically via its
 * {@code @MappedTypes} / {@code @MappedJdbcTypes} annotations when entities
 * declare {@code autoResultMap = true} on their {@code @TableName}.
 */
@Configuration
public class RobotModuleConfig {
}
