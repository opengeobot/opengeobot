/*
 * Function: CreateDrillRequest DTO — request body for creating a recovery drill
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a disaster recovery drill. Jackson serialises
 * field names in snake_case globally.
 */
public record CreateDrillRequest(
        @NotBlank(message = "type must not be blank")
        String type,

        String notes
) {
}
