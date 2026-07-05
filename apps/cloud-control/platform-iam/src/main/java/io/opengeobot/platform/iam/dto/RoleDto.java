/*
 * Function: Role DTO — role representation with permission codes for API responses
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Role definition bundling a set of permissions. Field names are serialised
 * in snake_case.
 *
 * @param roleId          stable public identifier of the role
 * @param roleName        human-readable name of the role
 * @param roleCode        stable, unique code identifying the role
 * @param description     optional explanation of the role purpose
 * @param status          lifecycle status — ACTIVE or DISABLED
 * @param sortOrder       sort order for display
 * @param builtIn        whether the role is built-in and cannot be deleted
 * @param permissionCodes permission codes granted by this role
 * @param createdAt       UTC timestamp when the role was created
 * @param updatedAt       UTC timestamp when the role was last updated
 */
public record RoleDto(
        String roleId,
        String roleName,
        String roleCode,
        String description,
        String status,
        Integer sortOrder,
        Boolean builtIn,
        List<String> permissionCodes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
