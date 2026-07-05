/*
 * Function: Update robot request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Request body for updating mutable robot fields. Identity fields
 * ({@code robot_id}, {@code serial_number}, {@code model_id}) are not editable
 * through this endpoint. Only supplied fields are updated. Jackson serialises
 * field names in snake_case globally.
 */
public record UpdateRobotRequest(
        String name,

        String orgId
) {
}
