/*
 * Function: I18nResource DTO — API response model for i18n resources
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing an i18n resource entry in API responses. Jackson
 * serialises field names in snake_case via the global
 * {@code SNAKE_CASE} property naming strategy.
 */
public record I18nResourceDto(
        String resourceKey,
        String locale,
        String resourceValue,
        String module,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
