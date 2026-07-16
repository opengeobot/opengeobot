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
import io.opengeobot.platform.common.mission.MissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
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
    private static final String FIELD_STATE = "state";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_TRACE_ID = "trace_id";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final MissionService missionService;

    private volatile Dispatcher dispatcher;

    public EdgeStateListener(NatsConnectionManager connectionManager,
                             ObjectMapper objectMapper,
                             MissionService missionService) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.missionService = missionService;
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
     * mission if the state is COMPLETED or FAILED.
     */
    @SuppressWarnings("unchecked")
    void onMessage(Message msg) {
        try {
            String json = new String(msg.getData(), StandardCharsets.UTF_8);
            Map<String, Object> state = objectMapper.readValue(json, Map.class);

            String missionId = asString(state.get(FIELD_MISSION_ID));
            String robotId = asString(state.get(FIELD_ROBOT_ID));
            String edgeState = asString(state.get(FIELD_STATE));
            String traceId = asString(state.get(FIELD_TRACE_ID));

            if (traceId != null) {
                MDC.put("trace_id", traceId);
            }

            log.info("Received edge state update: robot={} mission={} state={}",
                    robotId, missionId, edgeState);

            if (missionId == null || missionId.isBlank()) {
                log.debug("Edge state update has no mission_id; ignoring");
                return;
            }

            if (STATE_COMPLETED.equals(edgeState)) {
                missionService.completeMission(missionId);
                log.info("Mission {} completed by edge state update", missionId);
            } else if (STATE_FAILED.equals(edgeState)) {
                String error = asString(state.get(FIELD_ERROR));
                missionService.failMission(missionId, error);
                log.info("Mission {} failed by edge state update: {}", missionId, error);
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
}
