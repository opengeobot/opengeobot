/*
 * Function: Edge state listener - subscribes to edge state updates via NATS
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.robot.monitor.MonitorEventPublisher;
import io.opengeobot.platform.common.mission.MissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Subscribes to edge state updates from the edge Safety Gateway via NATS core
 * pub/sub. Listens on the subject pattern {@code opengeobot.dev.edge.state.>}
 * and processes state messages to update mission lifecycle state.
 * <p>
 * When an edge state message indicates a mission has COMPLETED or FAILED, the
 * listener transitions the corresponding mission via {@link MissionService}.
 * The edge safety state is authoritative for physical state transitions (safety
 * red line #3).
 * <p>
 * Only instantiated when {@code opengeobot.nats.enabled=true} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "opengeobot.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EdgeStateListener {

    private static final Logger log = LoggerFactory.getLogger(EdgeStateListener.class);
    private static final String STATE_SUBJECT_PATTERN = "opengeobot.dev.edge.state.>";
    private static final String FIELD_ROBOT_ID = "robot_id";
    private static final String FIELD_MISSION_ID = "mission_id";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_TRACE_ID = "trace_id";
    private static final String FIELD_STEP_INDEX = "step_index";
    private static final String FIELD_STEP_STATUS = "step_status";
    private static final String STATE_EXECUTING = "EXECUTING";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final MissionService missionService;
    private final MonitorEventPublisher monitorEventPublisher;
    private final TraceRecorder traceRecorder;

    private volatile Dispatcher dispatcher;

    public EdgeStateListener(NatsConnectionManager connectionManager,
                             ObjectMapper objectMapper,
                             MissionService missionService,
                             MonitorEventPublisher monitorEventPublisher,
                             TraceRecorder traceRecorder) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.missionService = missionService;
        this.monitorEventPublisher = monitorEventPublisher;
        this.traceRecorder = traceRecorder;
    }

    @PostConstruct
    public void init() {
        try {
            if (!connectionManager.isConnected()) {
                boolean connected = connectionManager.tryConnect();
                if (!connected) {
                    log.warn("NATS not connected at startup; edge state listener will not subscribe. " +
                            "It will retry on reconnection events.");
                    return;
                }
            }

            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                log.warn("NATS connection is null; edge state listener will not subscribe");
                return;
            }

            dispatcher = connection.createDispatcher(this::onMessage);
            dispatcher.subscribe(STATE_SUBJECT_PATTERN);
            log.info("Subscribed to edge state updates on '{}'", STATE_SUBJECT_PATTERN);
        } catch (Exception e) {
            log.warn("Failed to subscribe to edge state updates: {}", e.getMessage());
        }
    }

    /**
     * NATS message handler. Parses the edge state message and transitions the
     * mission if the status is COMPLETED or FAILED. Step-level fields
     * (step_index, step_status) are used for progress visibility.
     */
    @SuppressWarnings("unchecked")
    void onMessage(Message msg) {
        try {
            String json = new String(msg.getData(), StandardCharsets.UTF_8);
            Map<String, Object> state = objectMapper.readValue(json, Map.class);

            String missionId = asString(state.get(FIELD_MISSION_ID));
            String robotId = asString(state.get(FIELD_ROBOT_ID));
            String status = asString(state.get(FIELD_STATUS));
            String traceId = asString(state.get(FIELD_TRACE_ID));

            if (traceId != null) {
                MDC.put("trace_id", traceId);
            }

            log.info("Received edge state update: robot={} mission={} status={}",
                    robotId, missionId, status);

            // Push robot state update to WebSocket subscribers
            if (robotId != null && !robotId.isBlank()) {
                Map<String, Object> robotExtra = new HashMap<>();
                robotExtra.put("mission_id", missionId);
                if (traceId != null) {
                    robotExtra.put("trace_id", traceId);
                }
                monitorEventPublisher.publishRobotUpdate(robotId, status, robotExtra);
            }

            if (missionId == null || missionId.isBlank()) {
                log.debug("Edge state update has no mission_id; ignoring");
                return;
            }

            // Parse step-level fields for progress tracking.
            Integer stepIndex = asInteger(state.get(FIELD_STEP_INDEX));
            String stepStatus = asString(state.get(FIELD_STEP_STATUS));
            String error = asString(state.get(FIELD_ERROR));

            // Step-level state handling: update step status for visibility.
            if (stepIndex != null && stepStatus != null) {
                // step_index from edge is 0-based, step_order in DB is 1-based.
                int stepOrder = stepIndex + 1;
                try {
                    missionService.updateStepStatus(missionId, stepOrder, stepStatus, error);
                } catch (Exception e) {
                    log.warn("Failed to update step status for mission {} step {}: {}",
                            missionId, stepOrder, e.getMessage());
                }

                // Record step execution fact for trace replay
                if (traceId != null) {
                    traceRecorder.recordFact(traceId, "mission.step_updated", missionId, "mission_step",
                            robotId, missionId, "edge",
                            Map.of("step_index", stepIndex, "step_status", stepStatus));
                }

                log.info("Mission {} step {} {}", missionId, stepIndex, stepStatus);
            }

            // Mission-level state transitions.
            if (STATE_COMPLETED.equals(status)) {
                missionService.completeMission(missionId);
                log.info("Mission {} completed by edge state update", missionId);
            } else if (STATE_FAILED.equals(status)) {
                missionService.failMission(missionId, error);
                log.info("Mission {} failed by edge state update: {}", missionId, error);
            } else if (STATE_EXECUTING.equals(status)) {
                log.debug("Mission {} is executing (step {} {})", missionId, stepIndex, stepStatus);
            }
        } catch (Exception e) {
            log.error("Failed to process edge state message: {}", e.getMessage(), e);
        } finally {
            MDC.remove("trace_id");
        }
    }

    @PreDestroy
    public void close() {
        if (dispatcher != null) {
            try {
                Connection connection = connectionManager.getConnection();
                if (connection != null) {
                    connection.closeDispatcher(dispatcher);
                    log.info("Unsubscribed from edge state updates");
                }
            } catch (Exception e) {
                log.warn("Error closing edge state dispatcher: {}", e.getMessage());
            }
        }
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
