/*
 * Function: Update i18n resource request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating an i18n resource. The {@code resource_key} and
 * {@code locale} cannot be changed.
 */
public record UpdateI18nResourceRequest(
        @NotBlank(message = "resource_value must not be blank")
        String resourceValue,

        String module,
        String description
) {
}
