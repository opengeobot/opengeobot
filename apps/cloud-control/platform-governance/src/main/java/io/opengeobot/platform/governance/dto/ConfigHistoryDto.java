/*
 * Function: Config history DTO — API response model for config version snapshots
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing a config version snapshot from the append-only
 * {@code sys_config_history} table.
 */
public record ConfigHistoryDto(
        String configKey,
        String configValue,
        String valueType,
        String module,
        Integer version,
        String changeType,
        String changedBy,
        OffsetDateTime changedAt,
        String traceId
) {
}
