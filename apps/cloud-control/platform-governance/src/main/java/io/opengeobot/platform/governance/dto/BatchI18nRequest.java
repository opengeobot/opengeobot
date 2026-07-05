/*
 * Function: Batch i18n import request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import java.util.List;

/**
 * Request body for batch importing i18n resources. Each entry is upserted by
 * {@code resource_key} + {@code locale}. The operation is idempotent.
 */
public record BatchI18nRequest(
        List<I18nResourceEntry> resources
) {

    /**
     * A single upsertable i18n resource entry inside a batch request.
     */
    public record I18nResourceEntry(
            String resourceKey,
            String locale,
            String resourceValue,
            String module,
            String description
    ) {
    }
}
