/*
 * Function: Update robot model request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Request body for updating mutable robot model fields. The {@code model_id}
 * and {@code model_name} are not editable after creation. Only supplied fields
 * are updated. Jackson serialises field names in snake_case globally.
 */
public record UpdateRobotModelRequest(
        String manufacturer,

        String description,

        String capabilities
) {
}
