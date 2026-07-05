/*
 * Function: Update role request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.Size;

/**
 * Mutable fields of a role. The {@code roleCode} cannot be changed.
 * Field names are serialised in snake_case.
 *
 * @param roleName    human-readable name of the role
 * @param description optional explanation of the role purpose
 * @param status      lifecycle status — ACTIVE or DISABLED
 */
public record UpdateRoleRequest(
        @Size(max = 128) String roleName,
        String description,
        String status
) {
}
