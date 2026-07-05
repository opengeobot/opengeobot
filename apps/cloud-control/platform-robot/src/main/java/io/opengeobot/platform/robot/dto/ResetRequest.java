/*
 * Function: ResetRequest DTO — request body for resetting safety state
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Request body for resetting the safety state. When {@code robotId} is null
 * the reset applies to all robots in the {@code EMERGENCY_STOPPED} state.
 *
 * @param robotId optional robot identifier; null means reset all
 */
public record ResetRequest(
        String robotId
) {
}
