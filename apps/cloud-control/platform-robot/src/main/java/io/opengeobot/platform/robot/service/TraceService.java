/*
 * Function: Trace service — list, getByTraceId and replay for F-TRACE-001
 * Time: 2026-07-05
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for trace inspection and replay (F-TRACE-001). Provides listing of
 * root spans (those without a parent) with optional filters, detailed trace
 * view including all spans and fact events, and replay data extraction.
 * Trace data is read-only; no mutations are performed.
 */
@Service
public class TraceService {

    private static final Logger log = LoggerFactory.getLogger(TraceService.class);

    private final TraceSpanRepository traceSpanRepository;
    private final FactEventRepository factEventRepository;

    public TraceService(TraceSpanRepository traceSpanRepository,
                        FactEventRepository factEventRepository) {
        this.traceSpanRepository = traceSpanRepository;
        this.factEventRepository = factEventRepository;
    }

    public PageResult<TraceSpanDto> listTraces(String traceId, String robotId, String missionId,
                                                OffsetDateTime startTime, OffsetDateTime endTime,
                                                PageRequest pageRequest) {
        LambdaQueryWrapper<TraceSpan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(traceId != null && !traceId.isBlank(), TraceSpan::getTraceId, traceId)
                .eq(robotId != null && !robotId.isBlank(), TraceSpan::getRobotId, robotId)
                .eq(missionId != null && !missionId.isBlank(), TraceSpan::getMissionId, missionId)
                .ge(startTime != null, TraceSpan::getStartTime, startTime)
                .le(endTime != null, TraceSpan::getStartTime, endTime)
                .isNull(TraceSpan::getParentSpanId)
                .orderByDesc(TraceSpan::getStartTime);
        Page<TraceSpan> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<TraceSpan> result = traceSpanRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(TraceService::toSpanDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public TraceDetailDto getByTraceId(String traceId) {
        LambdaQueryWrapper<TraceSpan> spanWrapper = new LambdaQueryWrapper<>();
        spanWrapper.eq(TraceSpan::getTraceId, traceId)
                .orderByAsc(TraceSpan::getStartTime);
        List<TraceSpan> spans = traceSpanRepository.selectList(spanWrapper);
        if (spans.isEmpty()) {
            throw new ResourceNotFoundException("Trace '" + traceId + "' not found");
        }

        LambdaQueryWrapper<FactEvent> eventWrapper = new LambdaQueryWrapper<>();
        eventWrapper.eq(FactEvent::getTraceId, traceId)
                .orderByAsc(FactEvent::getOccurredAt);
        List<FactEvent> events = factEventRepository.selectList(eventWrapper);

        log.debug("Retrieved trace {} with {} spans and {} fact events", traceId, spans.size(), events.size());
        return new TraceDetailDto(
                traceId,
                spans.stream().map(TraceService::toSpanDto).toList(),
                events.stream().map(TraceService::toEventDto).toList()
        );
    }

    public List<FactEventDto> getReplay(String traceId) {
        LambdaQueryWrapper<FactEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FactEvent::getTraceId, traceId)
                .orderByAsc(FactEvent::getOccurredAt);
        List<FactEvent> events = factEventRepository.selectList(wrapper);
        if (events.isEmpty()) {
            LambdaQueryWrapper<TraceSpan> spanCheck = new LambdaQueryWrapper<>();
            spanCheck.eq(TraceSpan::getTraceId, traceId);
            if (traceSpanRepository.selectCount(spanCheck) == 0) {
                throw new ResourceNotFoundException("Trace '" + traceId + "' not found");
            }
        }
        return events.stream().map(TraceService::toEventDto).toList();
    }

    private static TraceSpanDto toSpanDto(TraceSpan entity) {
        return new TraceSpanDto(
                entity.getTraceId(),
                entity.getSpanId(),
                entity.getParentSpanId(),
                entity.getOperation(),
                entity.getService(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getDurationMs(),
                entity.getTags(),
                entity.getStatus(),
                entity.getRobotId(),
                entity.getMissionId()
        );
    }

    private static FactEventDto toEventDto(FactEvent entity) {
        return new FactEventDto(
                entity.getTraceId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getOccurredAt(),
                entity.getActorId(),
                entity.getRobotId(),
                entity.getMissionId()
        );
    }
}
