/*
 * Function: TraceSpan DTO — API response model for trace spans
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a trace span. Maps the {@code trace.trace_span}
 * entity to the OpenAPI contract {@code TraceSpan} schema.
 *
 * @param traceId      end-to-end trace identifier
 * @param spanId       unique span identifier within the trace
 * @param parentSpanId optional parent span identifier
 * @param operation    name of the operation performed
 * @param service      name of the service that executed the span
 * @param startTime    UTC timestamp when the span started
 * @param endTime      UTC timestamp when the span ended
 * @param durationMs   duration in milliseconds
 * @param tags         key-value tags
 * @param status       span status (OK, ERROR, CANCELLED)
 * @param robotId      optional robot identifier
 * @param missionId    optional mission identifier
 */
public record TraceSpanDto(
        String traceId,
        String spanId,
        String parentSpanId,
        String operation,
        String service,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Long durationMs,
        Map<String, Object> tags,
        String status,
        String robotId,
        String missionId
) {
}
