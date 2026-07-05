/*
 * Function: RestoreRequest DTO — request body for triggering a restore
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for triggering a restore from a backup. Jackson serialises
 * field names in snake_case globally.
 */
public record RestoreRequest(
        @NotBlank(message = "backup_id must not be blank")
        String backupId
) {
}
