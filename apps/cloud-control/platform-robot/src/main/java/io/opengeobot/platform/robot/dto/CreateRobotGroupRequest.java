/*
 * Function: Create robot group request DTO
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new robot group. When {@code parent_id} is
 * provided the group is created as a child of that group, and its
 * {@code path} is derived from the parent path. Jackson serialises field
 * names in snake_case globally.
 */
public record CreateRobotGroupRequest(
        String parentId,

        @NotBlank(message = "group_name must not be blank")
        String groupName,

        String description
) {
}
