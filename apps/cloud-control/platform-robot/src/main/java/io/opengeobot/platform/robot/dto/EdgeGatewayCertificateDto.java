/*
 * Function: Edge gateway certificate DTO — API response model for F-EDGE-001
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for an edge gateway certificate record. Jackson serialises
 * field names in snake_case globally.
 */
public record EdgeGatewayCertificateDto(
        String certId,
        String gatewayId,
        String fingerprint,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        String status,
        OffsetDateTime createdAt
) {
}
