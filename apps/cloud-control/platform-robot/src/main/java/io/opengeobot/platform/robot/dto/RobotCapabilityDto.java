/*
 * Function: Robot capability DTO — API model for a declared robot capability
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.util.Map;

/**
 * API model for a capability declared by a robot. {@code capabilityType} is a
 * stable code (e.g. navigation, perception) and {@code capabilityValue} is a
 * machine-readable scalar or flag. {@code details} carries capability-specific
 * structured metadata. Jackson serialises field names in snake_case globally.
 */
public record RobotCapabilityDto(
        String capabilityType,
        String capabilityValue,
        Map<String, Object> details
) {
}
