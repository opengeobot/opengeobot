/*
 * Function: Edge command dispatcher - publishes commands to edge Safety Gateway via NATS JetStream
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.robot.dto.EdgeCommandDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Dispatches edge commands to the edge Safety Gateway via NATS JetStream.
 * Commands are published to {@code opengeobot.dev.edge.command.{robotId}} and
 * must pass through the Safety Gateway before any physical action is taken
 * (safety red line #2).
 * <p>
 * The dispatcher uses JetStream for durable, at-least-once delivery. If NATS
 * is unavailable, the command is logged as failed but the calling transaction
 * is allowed to proceed - the mission state already reflects the intent and
 * the outbox relay will eventually publish the corresponding event.
 * <p>
 * Only instantiated when {@code opengeobot.nats.enabled=true} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "opengeobot.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EdgeCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EdgeCommandDispatcher.class);
    private static final String COMMAND_SUBJECT_PREFIX = "opengeobot.dev.edge.command.";

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public EdgeCommandDispatcher(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an edge command to the robot's edge Safety Gateway via NATS
     * JetStream. The command is serialised as JSON and published to the subject
     * {@code opengeobot.dev.edge.command.{robotId}}.
     *
     * @param robotId the target robot identifier
     * @param command the edge command to dispatch
     * @throws IllegalStateException if the command cannot be serialised or NATS is unavailable
     */
    public void dispatch(String robotId, EdgeCommandDto command) {
        String traceId = command.traceId() != null ? command.traceId() : "unknown";
        MDC.put("trace_id", traceId);
        try {
            if (!connectionManager.isConnected()) {
                boolean connected = connectionManager.tryConnect();
                if (!connected) {
                    log.error("NATS not connected; cannot dispatch edge command {} for robot {}",
                            command.commandId(), robotId);
                    throw new IllegalStateException(
                            "NATS is not connected; cannot dispatch edge command " + command.commandId());
                }
            }

            JetStream jetStream = connectionManager.getJetStream();
            if (jetStream == null) {
                log.error("JetStream not available; cannot dispatch edge command {} for robot {}",
                        command.commandId(), robotId);
                throw new IllegalStateException(
                        "JetStream is not available; cannot dispatch edge command " + command.commandId());
            }

            String subject = COMMAND_SUBJECT_PREFIX + robotId;
            byte[] data = objectMapper.writeValueAsBytes(command);

            PublishOptions options = PublishOptions.builder()
                    .messageId(command.commandId())
                    .build();

            PublishAck ack = jetStream.publish(subject, data, options);
            log.info("Dispatched edge command {} type={} to robot={} streamSeq={}",
                    command.commandId(), command.commandType(), robotId, ack.getSeqno());
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            log.error("Failed to dispatch edge command {} for robot {}: {}",
                    command.commandId(), robotId, e.getMessage(), e);
            throw new IllegalStateException("Failed to dispatch edge command " + command.commandId(), e);
        } finally {
            MDC.remove("trace_id");
        }
    }
}
