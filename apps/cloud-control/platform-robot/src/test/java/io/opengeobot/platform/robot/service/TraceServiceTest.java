/*
 * Function: Trace service unit tests — list, detail, replay
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.domain.FactEvent;
import io.opengeobot.platform.robot.domain.TraceSpan;
import io.opengeobot.platform.robot.dto.FactEventDto;
import io.opengeobot.platform.robot.dto.TraceDetailDto;
import io.opengeobot.platform.robot.dto.TraceSpanDto;
import io.opengeobot.platform.robot.repository.FactEventRepository;
import io.opengeobot.platform.robot.repository.TraceSpanRepository;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TraceService}. Covers trace listing, detailed trace
 * view with spans and fact events, and replay data extraction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TraceServiceTest {

    @Mock private TraceSpanRepository traceSpanRepository;
    @Mock private FactEventRepository factEventRepository;

    private TraceService service;

    @BeforeEach
    void setUp() {
        service = new TraceService(traceSpanRepository, factEventRepository);
    }

    private TraceSpan createSpan(String traceId, String spanId, String operation) {
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(spanId);
        span.setOperation(operation);
        span.setService("cloud-control");
        span.setStartTime(OffsetDateTime.now(ZoneOffset.UTC));
        span.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1));
        span.setDurationMs(1000L);
        span.setStatus("OK");
        span.setRobotId("rbt_001");
        span.setMissionId("msn_001");
        return span;
    }

    private FactEvent createEvent(String traceId, String eventType) {
        FactEvent event = new FactEvent();
        event.setTraceId(traceId);
        event.setEventType(eventType);
        event.setPayload(Map.of("key", "value"));
        event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setActorId("user_001");
        event.setRobotId("rbt_001");
        event.setMissionId("msn_001");
        return event;
    }

    @Test
    void listTraces_returnsPagedRootSpans() {
        TraceSpan span = createSpan("trace_001", "span_001", "mission.start");
        Page<TraceSpan> page = new Page<>(1, 10);
        page.setRecords(List.of(span));
        page.setTotal(1);
        when(traceSpanRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<TraceSpanDto> result = service.listTraces(null, null, null, null, null, PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals("trace_001", result.items().get(0).traceId());
        assertEquals("mission.start", result.items().get(0).operation());
    }

    @Test
    void listTraces_emptyResultReturnsEmptyList() {
        Page<TraceSpan> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(traceSpanRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<TraceSpanDto> result = service.listTraces("trace_999", null, null, null, null, PageRequest.of(1, 10));

        assertTrue(result.items().isEmpty());
    }

    @Test
    void getByTraceId_returnsDetailWithSpansAndEvents() {
        TraceSpan span1 = createSpan("trace_001", "span_001", "mission.start");
        TraceSpan span2 = createSpan("trace_001", "span_002", "mission.step");
        when(traceSpanRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(span1, span2));
        FactEvent event = createEvent("trace_001", "mission.started");
        when(factEventRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(event));

        TraceDetailDto result = service.getByTraceId("trace_001");

        assertEquals("trace_001", result.traceId());
        assertEquals(2, result.spans().size());
        assertEquals(1, result.factEvents().size());
        assertEquals("mission.started", result.factEvents().get(0).eventType());
    }

    @Test
    void getByTraceId_notFoundThrowsResourceNotFound() {
        when(traceSpanRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.getByTraceId("trace_999"));
    }

    @Test
    void getByTraceId_noEventsReturnsEmptyEventList() {
        TraceSpan span = createSpan("trace_001", "span_001", "op");
        when(traceSpanRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(span));
        when(factEventRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        TraceDetailDto result = service.getByTraceId("trace_001");

        assertEquals(1, result.spans().size());
        assertTrue(result.factEvents().isEmpty());
    }

    @Test
    void getReplay_returnsOrderedFactEvents() {
        FactEvent event1 = createEvent("trace_001", "mission.started");
        FactEvent event2 = createEvent("trace_001", "mission.step_completed");
        when(factEventRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(event1, event2));

        List<FactEventDto> result = service.getReplay("trace_001");

        assertEquals(2, result.size());
        assertEquals("mission.started", result.get(0).eventType());
        assertEquals("mission.step_completed", result.get(1).eventType());
    }

    @Test
    void getReplay_noEventsButTraceExistsReturnsEmpty() {
        when(factEventRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(traceSpanRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        List<FactEventDto> result = service.getReplay("trace_001");

        assertTrue(result.isEmpty());
    }

    @Test
    void getReplay_traceNotFoundThrowsResourceNotFound() {
        when(factEventRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(traceSpanRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThrows(ResourceNotFoundException.class, () -> service.getReplay("trace_999"));
    }
}
