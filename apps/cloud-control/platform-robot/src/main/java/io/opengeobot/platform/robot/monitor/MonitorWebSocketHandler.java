/*
 * Function: Monitor WebSocket handler — manages real-time monitor sessions and broadcasts for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.monitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler for the {@code /ws/monitor} endpoint. Maintains the set of
 * connected sessions and supports per-session subscriptions to a specific
 * robot or mission. The {@link MonitorEventPublisher} pushes robot status and
 * mission progress updates through this handler so subscribers receive
 * near-real-time telemetry without polling the REST API.
 *
 * <p>Subscription protocol: a client may send a JSON message of the form
 * {@code {"action":"subscribe","topic":"robot","robot_id":"rbt_..."}} or
 * {@code {"action":"subscribe","topic":"mission","mission_id":"mission_..."}}.
 * Unsubscribe with {@code action=unsubscribe}. An absent subscription means
 * the session receives all broadcasts.
 */
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorWebSocketHandler.class);

    private final ObjectMapper objectMapper;

    /**
     * Active sessions. A session is added on connect and removed on close.
     */
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    /**
     * Per-session subscriptions. Keyed by session id. A subscription entry
     * holds the optional robot_id and/or mission_id the session is interested
     * in. An empty entry means "all updates".
     */
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public MonitorWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        subscriptions.put(session.getId(), new Subscription(null, null));
        log.info("Monitor WebSocket session opened: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            SubscriptionCommand command = objectMapper.readValue(message.getPayload(), SubscriptionCommand.class);
            if ("subscribe".equalsIgnoreCase(command.action())) {
                subscriptions.put(session.getId(), new Subscription(command.robotId(), command.missionId()));
                log.debug("Session {} subscribed to robot={} mission={}", session.getId(), command.robotId(), command.missionId());
            } else if ("unsubscribe".equalsIgnoreCase(command.action())) {
                subscriptions.put(session.getId(), new Subscription(null, null));
                log.debug("Session {} unsubscribed", session.getId());
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse subscription message from session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        subscriptions.remove(session.getId());
        log.info("Monitor WebSocket session closed: {} status={} (total: {})", session.getId(), status, sessions.size());
    }

    /**
     * Broadcasts a robot status update to subscribed sessions. Sessions
     * subscribed to the specific robot_id, plus sessions with no subscription
     * filter, receive the message.
     */
    public void broadcastRobotUpdate(String robotId, Object payload) {
        String json = toJson(payload);
        if (json == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            Subscription sub = subscriptions.get(session.getId());
            if (sub == null || sub.matchesRobot(robotId)) {
                send(session, json);
            }
        }
    }

    /**
     * Broadcasts a mission progress update to subscribed sessions.
     */
    public void broadcastMissionUpdate(String missionId, String robotId, Object payload) {
        String json = toJson(payload);
        if (json == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            Subscription sub = subscriptions.get(session.getId());
            if (sub == null || sub.matchesMission(missionId, robotId)) {
                send(session, json);
            }
        }
    }

    /**
     * Broadcasts an overview update to all connected sessions.
     */
    public void broadcastOverview(Object payload) {
        String json = toJson(payload);
        if (json == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                send(session, json);
            }
        }
    }

    /**
     * Returns the number of currently connected sessions.
     */
    public int sessionCount() {
        return sessions.size();
    }

    private void send(WebSocketSession session, String json) {
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to send monitor update to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise monitor payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Per-session subscription filter. Both fields may be null, meaning the
     * session receives all updates.
     */
    private record Subscription(String robotId, String missionId) {

        boolean matchesRobot(String robotId) {
            return this.robotId == null || this.robotId.equals(robotId);
        }

        boolean matchesMission(String missionId, String robotId) {
            if (this.missionId != null) {
                return this.missionId.equals(missionId);
            }
            return matchesRobot(robotId);
        }
    }

    /**
     * Inbound subscription command parsed from the client message.
     */
    private record SubscriptionCommand(String action, String topic, String robotId, String missionId) {
    }
}
