/*
 * Function: Update robot status request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for transitioning the robot operational status. The transition
 * is validated against the SM-ROBOT-001 state machine. The {@code reason} is
 * recorded in the status history and audit log. Jackson serialises field names
 * in snake_case globally.
 */
public record UpdateRobotStatusRequest(
        @NotBlank(message = "status must not be blank")
        String status,

        String reason
) {
}
