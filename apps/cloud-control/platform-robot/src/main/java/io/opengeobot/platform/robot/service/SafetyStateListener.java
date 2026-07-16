/*
 * Function: Safety state listener - subscribes to edge safety gateway state changes via NATS
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.robot.monitor.MonitorEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Subscribes to safety state change notifications from the edge Safety Gateway
 * via NATS core pub/sub. Listens on the subject
 * {@code opengeobot.dev.edge.{gatewayId}.safety.state_changed} where the
 * gateway id is configurable via {@code opengeobot.edge.safety.gateway-id}
 * (default {@code edge_01}).
 * <p>
 * When a safety state change is received, the listener:
 * <ul>
 *   <li>Logs the transition for audit purposes</li>
 *   <li>Pushes a {@code SAFETY_<state>} robot update to WebSocket subscribers</li>
 *   <li>Records a {@code safety.state_changed} fact event for trace replay</li>
 * </ul>
 * <p>
 * Only instantiated when {@code opengeobot.nats.enabled=true} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "opengeobot.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SafetyStateListener {

    private static final Logger log = LoggerFactory.getLogger(SafetyStateListener.class);
    private static final String SAFETY_SUBJECT_PREFIX = "opengeobot.dev.edge.";
    private static final String SAFETY_SUBJECT_SUFFIX = ".safety.state_changed";
    private static final String FIELD_ROBOT_ID = "robot_id";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_PREVIOUS_STATE = "previous_state";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_TRACE_ID = "trace_id";

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final MonitorEventPublisher monitorEventPublisher;
    private final TraceRecorder traceRecorder;
    private final PublicIdGenerator publicIdGenerator;
    private final String gatewayId;

    private volatile Dispatcher dispatcher;

    public SafetyStateListener(NatsConnectionManager connectionManager,
                               ObjectMapper objectMapper,
                               MonitorEventPublisher monitorEventPublisher,
                               TraceRecorder traceRecorder,
                               PublicIdGenerator publicIdGenerator,
                               @Value("${opengeobot.edge.safety.gateway-id:edge_01}") String gatewayId) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.monitorEventPublisher = monitorEventPublisher;
        this.traceRecorder = traceRecorder;
        this.publicIdGenerator = publicIdGenerator;
        this.gatewayId = gatewayId;
    }

    @PostConstruct
    public void init() {
        try {
            if (!connectionManager.isConnected()) {
                boolean connected = connectionManager.tryConnect();
                if (!connected) {
                    log.warn("NATS not connected at startup; safety state listener will not subscribe. " +
                            "It will retry on reconnection events.");
                    return;
                }
            }

            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                log.warn("NATS connection is null; safety state listener will not subscribe");
                return;
            }

            String subject = SAFETY_SUBJECT_PREFIX + gatewayId + SAFETY_SUBJECT_SUFFIX;
            dispatcher = connection.createDispatcher(this::onMessage);
            dispatcher.subscribe(subject);
            log.info("Subscribed to safety state changes on '{}' for gateway '{}'", subject, gatewayId);
        } catch (Exception e) {
            log.warn("Failed to subscribe to safety state changes: {}", e.getMessage());
        }
    }

    /**
     * NATS message handler. Parses the safety state change message, logs it,
     * pushes a robot update to WebSocket subscribers, and records a fact event
     * for trace replay.
     */
    @SuppressWarnings("unchecked")
    void onMessage(Message msg) {
        try {
            String json = new String(msg.getData(), StandardCharsets.UTF_8);
            Map<String, Object> state = objectMapper.readValue(json, Map.class);

            String robotId = asString(state.get(FIELD_ROBOT_ID));
            String safetyState = asString(state.get(FIELD_STATE));
            String previousState = asString(state.get(FIELD_PREVIOUS_STATE));
            String reason = asString(state.get(FIELD_REASON));
            String traceId = asString(state.get(FIELD_TRACE_ID));

            if (traceId != null) {
                MDC.put("trace_id", traceId);
            }

            log.warn("Safety state change: robot={} state={} previous={} reason={}",
                    robotId, safetyState, previousState, reason);

            // Push safety update to WebSocket subscribers
            if (robotId != null && !robotId.isBlank() && safetyState != null) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("safety_state", safetyState);
                if (previousState != null) {
                    extra.put("previous_state", previousState);
                }
                if (reason != null) {
                    extra.put("reason", reason);
                }
                monitorEventPublisher.publishRobotUpdate(robotId, "SAFETY_" + safetyState, extra);
            }

            // Record fact event for trace replay
            String effectiveTraceId = traceId != null ? traceId : publicIdGenerator.generate("trace");
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("safety_state", safetyState);
            attrs.put("previous_state", previousState);
            attrs.put("reason", reason);
            attrs.put("gateway_id", gatewayId);
            String entityId = robotId != null ? robotId : gatewayId;
            traceRecorder.recordFact(effectiveTraceId, "safety.state_changed",
                    entityId, "safety", robotId, null, "edge", attrs);

        } catch (Exception e) {
            log.error("Failed to process safety state message: {}", e.getMessage(), e);
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
                    log.info("Unsubscribed from safety state changes");
                }
            } catch (Exception e) {
                log.warn("Error closing safety state dispatcher: {}", e.getMessage());
            }
        }
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
