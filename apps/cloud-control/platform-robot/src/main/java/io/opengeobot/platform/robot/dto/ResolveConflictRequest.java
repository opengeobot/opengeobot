/*
 * Function: ResolveConflictRequest DTO — resolve a detected fleet conflict
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for resolving a detected conflict. The {@code resolution}
 * determines how the conflict is handled: reorder the lower-priority schedule
 * later, reassign it to another robot, or cancel it. Jackson serialises field
 * names in snake_case globally.
 *
 * @param resolution     resolution strategy (REORDER, REASSIGN, CANCEL)
 * @param targetRobotId  required when resolution is REASSIGN
 */
public record ResolveConflictRequest(
        @NotBlank(message = "resolution must not be blank")
        String resolution,

        String targetRobotId
) {
}
