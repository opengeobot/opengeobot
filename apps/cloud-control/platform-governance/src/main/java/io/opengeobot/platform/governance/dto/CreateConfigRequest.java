/*
 * Function: Create config request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new platform config entry. The {@code configKey}
 * must be unique; a duplicate returns {@code CONFIG_KEY_EXISTS}.
 */
public record CreateConfigRequest(
        @NotBlank(message = "config_key must not be blank")
        String configKey,

        @NotBlank(message = "config_value must not be blank")
        String configValue,

        @NotBlank(message = "value_type must not be blank")
        String valueType,

        @NotBlank(message = "module must not be blank")
        String module,

        String description,

        Boolean encrypted
) {
}
