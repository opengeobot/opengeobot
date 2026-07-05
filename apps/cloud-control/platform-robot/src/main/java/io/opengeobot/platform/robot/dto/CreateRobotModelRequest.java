/*
 * Function: Create robot model request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new robot model. The {@code model_name} must be
 * unique. Jackson serialises field names in snake_case globally.
 */
public record CreateRobotModelRequest(
        @NotBlank(message = "model_name must not be blank")
        String modelName,

        String manufacturer,

        String description,

        String capabilities
) {
}
