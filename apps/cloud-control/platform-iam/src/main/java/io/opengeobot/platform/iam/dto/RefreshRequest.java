/*
 * Function: Refresh request DTO — refresh token submitted to obtain a new access token
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh token submitted to obtain a new access token without re-entering
 * credentials. Field names are serialised in snake_case.
 *
 * @param refreshToken previously issued refresh token
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
