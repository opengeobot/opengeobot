/*
 * Function: Assign roles to user request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for replacing the full set of roles assigned to a user. The
 * previous assignments are removed and replaced by the supplied list.
 * Field names are serialised in snake_case.
 *
 * @param roleIds role identifiers to assign to the user
 */
public record AssignRolesRequest(
        @NotNull List<String> roleIds
) {
}
