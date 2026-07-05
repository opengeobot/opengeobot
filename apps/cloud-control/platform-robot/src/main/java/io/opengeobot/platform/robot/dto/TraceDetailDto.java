/*
 * Function: TraceDetail DTO — API response model for trace detail with spans and events
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.util.List;

/**
 * API response DTO for trace detail. Combines all spans and fact events
 * for a single trace, used for trace inspection and replay.
 *
 * @param traceId    the trace identifier
 * @param spans      all spans in the trace, ordered by start time
 * @param factEvents all fact events in the trace, ordered by occurrence
 */
public record TraceDetailDto(
        String traceId,
        List<TraceSpanDto> spans,
        List<FactEventDto> factEvents
) {
}
