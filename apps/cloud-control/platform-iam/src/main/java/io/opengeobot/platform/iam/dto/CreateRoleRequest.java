/*
 * Function: Create role request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new role. The {@code roleCode} must be unique.
 * Field names are serialised in snake_case.
 *
 * @param roleName    human-readable name of the role
 * @param roleCode    stable, unique code identifying the role
 * @param description optional explanation of the role purpose
 */
public record CreateRoleRequest(
        @NotBlank @Size(max = 128) String roleName,
        @NotBlank @Size(max = 128) String roleCode,
        String description
) {
}
