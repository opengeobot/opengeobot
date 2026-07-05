/*
 * Function: FactEvent DTO — API response model for fact events
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a fact event. Maps the {@code trace.fact_event}
 * entity to the OpenAPI contract {@code FactEvent} schema.
 *
 * @param traceId    trace identifier linking this event to the trace context
 * @param eventType  type of the fact event
 * @param payload    event-specific payload
 * @param occurredAt UTC timestamp when the event occurred
 * @param actorId    optional actor that triggered the event
 * @param robotId    optional robot identifier
 * @param missionId  optional mission identifier
 */
public record FactEventDto(
        String traceId,
        String eventType,
        Map<String, Object> payload,
        OffsetDateTime occurredAt,
        String actorId,
        String robotId,
        String missionId
) {
}
