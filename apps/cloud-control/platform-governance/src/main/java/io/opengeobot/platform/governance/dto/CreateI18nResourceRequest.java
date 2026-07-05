/*
 * Function: Create i18n resource request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating an i18n resource.
 */
public record CreateI18nResourceRequest(
        @NotBlank(message = "resource_key must not be blank")
        String resourceKey,

        @NotBlank(message = "locale must not be blank")
        String locale,

        @NotBlank(message = "resource_value must not be blank")
        String resourceValue,

        @NotBlank(message = "module must not be blank")
        String module,

        String description
) {
}
