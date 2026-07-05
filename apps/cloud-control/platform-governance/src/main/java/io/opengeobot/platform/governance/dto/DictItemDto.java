/*
 * Function: DictItem DTO — API response model for dictionary items
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Immutable DTO representing a dictionary item in API responses. Jackson
 * serialises field names in snake_case via the global
 * {@code SNAKE_CASE} property naming strategy.
 */
public record DictItemDto(
        String typeCode,
        String itemCode,
        String itemValue,
        String labelZhCn,
        String labelEnUs,
        Integer sortOrder,
        String status,
        Map<String, Object> extra,
        Integer version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
