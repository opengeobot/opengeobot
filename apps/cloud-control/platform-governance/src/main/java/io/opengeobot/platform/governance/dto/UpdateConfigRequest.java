/*
 * Function: Update config request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating an existing config entry. On success the
 * {@code version} is incremented and a history row is appended.
 */
public record UpdateConfigRequest(
        @NotBlank(message = "config_value must not be blank")
        String configValue,

        String description
) {
}
