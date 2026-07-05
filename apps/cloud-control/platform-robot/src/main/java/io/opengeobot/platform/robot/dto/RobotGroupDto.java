/*
 * Function: Robot group DTO — API response model for robot group entries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a robot group. Groups form a hierarchy via
 * {@code parent_id} and carry a materialised {@code path} for efficient
 * tree traversal. Jackson serialises field names in snake_case globally.
 *
 * @param groupId    public identifier (ULID-based, prefixed with {@code grp_})
 * @param parentId   identifier of the parent group, or null for a root group
 * @param groupName   human-friendly group name
 * @param description human-readable description
 * @param path        materialised path for ancestor/descendant queries
 * @param createdAt   UTC timestamp of creation
 * @param updatedAt   UTC timestamp of last update
 */
public record RobotGroupDto(
        String groupId,
        String parentId,
        String groupName,
        String description,
        String path,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
