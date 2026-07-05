/*
 * Function: Batch i18n import result DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

/**
 * Result of a batch i18n import operation, reporting the number of resources
 * created or updated and the number skipped due to validation errors.
 */
public record BatchI18nResultDto(
        int imported,
        int skipped
) {
}
