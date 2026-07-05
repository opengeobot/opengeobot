/*
 * Function: Config DTO — API response model for platform configs
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing a platform config entry in API responses.
 * Encrypted configs have their {@code configValue} masked by the service
 * layer before mapping to this DTO.
 */
public record ConfigDto(
        String configKey,
        String configValue,
        String valueType,
        String module,
        String description,
        Boolean encrypted,
        Integer version,
        String status,
        String createdBy,
        String updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
