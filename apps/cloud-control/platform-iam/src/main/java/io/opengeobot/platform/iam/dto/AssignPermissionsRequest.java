/*
 * Function: Assign permissions to role request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for replacing the full set of permissions granted to a role.
 * The previous grants are removed and replaced by the supplied list.
 * Field names are serialised in snake_case.
 *
 * @param permissionCodes permission codes to grant to the role
 */
public record AssignPermissionsRequest(
        @NotNull List<String> permissionCodes
) {
}
