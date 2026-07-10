/*
 * Function: Rotate edge gateway certificate request DTO
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Request body for rotating an edge gateway certificate. Jackson serialises
 * field names in snake_case globally.
 */
public record RotateCertificateRequest(
        @NotBlank(message = "fingerprint must not be blank")
        String fingerprint,

        OffsetDateTime issuedAt,

        @NotNull(message = "expires_at must not be null")
        OffsetDateTime expiresAt
) {
}
