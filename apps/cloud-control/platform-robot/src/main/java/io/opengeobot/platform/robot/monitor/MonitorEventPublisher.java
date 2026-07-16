/*
 * Function: Monitor event publisher - pushes real-time updates through the WebSocket handler for F-MONITOR-001
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes real-time monitor events to connected WebSocket clients via
 * {@link MonitorWebSocketHandler}. Mission and robot state changes produced by
 * the cloud application services are forwarded here so that subscribers of the
 * {@code /ws/monitor} endpoint receive near-real-time updates without polling.
 *
 * <p>Each method builds a JSON-serialisable payload map and delegates to the
 * matching broadcast method on the handler. The handler handles session
 * filtering and serialisation.
 */
@Component
public class MonitorEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MonitorEventPublisher.class);

    private final MonitorWebSocketHandler webSocketHandler;

    public MonitorEventPublisher(MonitorWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Publishes a mission status change to subscribed WebSocket sessions.
     *
     * @param missionId the mission identifier
     * @param robotId   the robot executing the mission (may be {@code null})
     * @param status    the new mission status (e.g. EXECUTING, COMPLETED, FAILED)
     * @param traceId   the trace id linking this event to the request chain
     */
    public void publishMissionUpdate(String missionId, String robotId, String status, String traceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "mission");
        payload.put("missionId", missionId);
        payload.put("robotId", robotId);
        payload.put("status", status);
        payload.put("traceId", traceId);
        payload.put("timestamp", Instant.now().toString());

        webSocketHandler.broadcastMissionUpdate(missionId, robotId, payload);
        log.debug("Published mission update: mission={} status={}", missionId, status);
    }

    /**
     * Publishes a robot status change to subscribed WebSocket sessions.
     *
     * @param robotId the robot identifier
     * @param status  the new robot status (e.g. ONLINE, SAFETY_EMERGENCY_STOP)
     * @param extra   additional fields to include in the payload (may be {@code null})
     */
    public void publishRobotUpdate(String robotId, String status, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        if (extra != null) {
            payload.putAll(extra);
        }
        payload.put("type", "robot");
        payload.put("robotId", robotId);
        payload.put("status", status);
        payload.put("timestamp", Instant.now().toString());

        webSocketHandler.broadcastRobotUpdate(robotId, payload);
        log.debug("Published robot update: robot={} status={}", robotId, status);
    }
}
