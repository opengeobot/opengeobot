/*
 * Function: DictType DTO — API response model for dictionary types
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing a dictionary type in API responses. Jackson
 * serialises field names in snake_case via the global
 * {@code SNAKE_CASE} property naming strategy.
 */
public record DictTypeDto(
        String typeCode,
        String typeName,
        String description,
        String status,
        Integer version,
        Integer publishedVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
