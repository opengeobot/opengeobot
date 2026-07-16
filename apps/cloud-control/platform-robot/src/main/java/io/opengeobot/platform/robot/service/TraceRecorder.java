/*
 * Function: Trace recorder - writes trace spans and fact events for F-TRACE-001
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.FactEvent;
import io.opengeobot.platform.robot.domain.TraceSpan;
import io.opengeobot.platform.robot.repository.FactEventRepository;
import io.opengeobot.platform.robot.repository.TraceSpanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes trace data (spans and fact events) to the {@code trace.trace_span} and
 * {@code trace.fact_event} tables. This complements the read-only
 * {@link TraceService} which lists and replays trace data.
 *
 * <p>Only key lifecycle events are recorded to avoid excessive database writes.
 * Each recorded fact includes the entity id and type in its payload so the
 * replay endpoint can reconstruct the full event sequence.
 */
@Component
public class TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);
    private static final String SPAN_STATUS_OK = "OK";

    private final TraceSpanRepository traceSpanRepository;
    private final FactEventRepository factEventRepository;
    private final PublicIdGenerator publicIdGenerator;
    private final ClockProvider clockProvider;

    public TraceRecorder(TraceSpanRepository traceSpanRepository,
                         FactEventRepository factEventRepository,
                         PublicIdGenerator publicIdGenerator,
                         ClockProvider clockProvider) {
        this.traceSpanRepository = traceSpanRepository;
        this.factEventRepository = factEventRepository;
        this.publicIdGenerator = publicIdGenerator;
        this.clockProvider = clockProvider;
    }

    /**
     * Records a trace span with timing and tags.
     *
     * @param traceId       the trace identifier linking all spans in a trace
     * @param spanId        the unique span identifier (may be generated if {@code null})
     * @param parentSpanId  the parent span id, or {@code null} for a root span
     * @param operation     the operation name (e.g. "mission.start")
     * @param service       the service that produced the span (e.g. "cloud-control")
     * @param robotId       the associated robot id, or {@code null}
     * @param missionId     the associated mission id, or {@code null}
     * @param tags          additional span tags, or {@code null}
     */
    public void recordSpan(String traceId, String spanId, String parentSpanId,
                           String operation, String service,
                           String robotId, String missionId,
                           Map<String, Object> tags) {
        OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock().withZone(ZoneOffset.UTC));

        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(spanId != null ? spanId : publicIdGenerator.generate("span"));
        span.setParentSpanId(parentSpanId);
        span.setOperation(operation);
        span.setService(service);
        span.setStartTime(now);
        span.setEndTime(now);
        span.setDurationMs(0L);
        span.setTags(tags);
        span.setStatus(SPAN_STATUS_OK);
        span.setRobotId(robotId);
        span.setMissionId(missionId);

        traceSpanRepository.insert(span);
        log.debug("Recorded trace span: trace={} span={} operation={}", traceId, spanId, operation);
    }

    /**
     * Records an immutable fact event linked to a trace. The entity id and
     * type are included in the payload so replay can reconstruct context.
     *
     * @param traceId    the trace identifier
     * @param eventType  the event type (e.g. "mission.created", "safety.state_changed")
     * @param entityId   the id of the entity this event relates to
     * @param entityType the type of entity (e.g. "mission", "robot", "safety")
     * @param robotId    the associated robot id, or {@code null}
     * @param missionId  the associated mission id, or {@code null}
     * @param actorId    the actor that triggered the event, or {@code null}
     * @param attributes additional payload data, or {@code null}
     */
    public void recordFact(String traceId, String eventType,
                           String entityId, String entityType,
                           String robotId, String missionId,
                           String actorId, Map<String, Object> attributes) {
        OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock().withZone(ZoneOffset.UTC));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entity_id", entityId);
        payload.put("entity_type", entityType);
        payload.put("event_type", eventType);
        payload.put("occurred_at", now.toString());
        if (attributes != null) {
            payload.putAll(attributes);
        }

        FactEvent event = new FactEvent();
        event.setTraceId(traceId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setOccurredAt(now);
        event.setActorId(actorId);
        event.setRobotId(robotId);
        event.setMissionId(missionId);

        factEventRepository.insert(event);
        log.debug("Recorded fact event: trace={} type={} entity={}/{}", traceId, eventType, entityType, entityId);
    }
}
