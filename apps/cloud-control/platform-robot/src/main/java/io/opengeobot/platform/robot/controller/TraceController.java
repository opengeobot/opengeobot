/*
 * Function: Trace REST controller — endpoints for trace listing, detail and replay
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.FactEventDto;
import io.opengeobot.platform.robot.dto.TraceDetailDto;
import io.opengeobot.platform.robot.dto.TraceSpanDto;
import io.opengeobot.platform.robot.service.TraceService;
import io.opengeobot.platform.robot.web.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for trace inspection and replay. Exposes endpoints under
 * {@code /api/v1/traces} per the OpenAPI contract. Trace data is read-only.
 * Permissions: {@code trace.trace.read} for all endpoints.
 */
@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('trace.trace.read')")
    public PageResponse<TraceSpanDto> listTraces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String missionId,
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime) {
        PageResult<TraceSpanDto> result = traceService.listTraces(
                traceId, robotId, missionId, startTime, endTime, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @GetMapping("/{traceId}")
    @PreAuthorize("hasAuthority('trace.trace.read')")
    public TraceDetailDto getTrace(@PathVariable String traceId) {
        return traceService.getByTraceId(traceId);
    }

    @GetMapping("/{traceId}/replay")
    @PreAuthorize("hasAuthority('trace.trace.replay')")
    public Map<String, Object> getReplay(@PathVariable String traceId) {
        List<FactEventDto> events = traceService.getReplay(traceId);
        return Map.of("trace_id", traceId, "events", events);
    }
}
