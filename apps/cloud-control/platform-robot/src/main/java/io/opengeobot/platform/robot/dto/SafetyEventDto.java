/*
 * Function: SafetyEvent DTO — API response model for safety events
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for a safety event. Maps the {@code policy.safety_event}
 * entity to the OpenAPI contract {@code SafetyEvent} schema.
 *
 * @param eventId    unique event identifier
 * @param robotId    robot identifier affected by this event
 * @param eventType  type of the event (EMERGENCY_STOP, RESET, etc.)
 * @param reason     human-readable reason
 * @param occurredAt UTC timestamp of the event
 * @param traceId    end-to-end trace identifier
 * @param actorId    actor that triggered the event
 */
public record SafetyEventDto(
        String eventId,
        String robotId,
        String eventType,
        String reason,
        OffsetDateTime occurredAt,
        String traceId,
        String actorId
) {
}
