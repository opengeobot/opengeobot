/*
 * Function: EmergencyStopRequest DTO — request body for triggering emergency stop
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Request body for triggering an emergency stop. When {@code robotId} is
 * null the stop applies to all robots (global emergency stop).
 *
 * @param robotId optional robot identifier; null means global
 * @param reason  human-readable reason for the stop
 */
public record EmergencyStopRequest(
        String robotId,
        String reason
) {
}
