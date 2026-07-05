/*
 * Function: Create robot request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request body for registering a new robot. The {@code serial_number} must be
 * unique; a duplicate returns {@code CONFLICT}. The robot is created with an
 * initial status of {@code OFFLINE} until the first heartbeat is received.
 * Jackson serialises field names in snake_case globally.
 */
public record CreateRobotRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotBlank(message = "model_id must not be blank")
        String modelId,

        @NotBlank(message = "serial_number must not be blank")
        String serialNumber,

        @NotBlank(message = "org_id must not be blank")
        String orgId,

        List<RobotCapabilityDto> capabilities
) {
}
