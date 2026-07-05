/*
 * Function: Update robot group request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Request body for updating mutable robot group fields. The {@code group_id}
 * and {@code parent_id} are not editable after creation. Only supplied fields
 * are updated. Jackson serialises field names in snake_case globally.
 */
public record UpdateRobotGroupRequest(
        String groupName,

        String description
) {
}
