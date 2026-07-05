/*
 * Function: SafetyState DTO — API response model for safety state
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a safety state. Maps the {@code policy.safety_state}
 * entity to the OpenAPI contract {@code SafetyState} schema. Jackson
 * serialises field names in snake_case globally.
 *
 * @param robotId      robot identifier this state belongs to
 * @param state        current safety state (NORMAL, EMERGENCY_STOPPED, RESETTING)
 * @param lastEventAt  timestamp of the last safety event
 * @param reason       optional reason for the current state
 * @param updatedAt    timestamp of the last state update
 */
public record SafetyStateDto(
        String robotId,
        String state,
        OffsetDateTime lastEventAt,
        String reason,
        OffsetDateTime updatedAt
) {
}
