/*
 * Function: Update user status request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for transitioning the user account status. The {@code status} is
 * a code contract; {@code reason} is a machine-readable audit code.
 * Field names are serialised in snake_case.
 *
 * @param status target account status — ACTIVE, DISABLED or LOCKED
 * @param reason machine-readable reason for the status transition
 */
public record UpdateUserStatusRequest(
        @NotBlank String status,
        String reason
) {
}
