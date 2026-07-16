/*
 * Function: Agent-runtime NATS client - request-reply to QwenPaw for mission planning
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.robot.dto.MissionContextDto;
import io.opengeobot.platform.robot.dto.PlanProposalDto;
import io.opengeobot.platform.robot.dto.ReplanRequestDto;
import io.opengeobot.platform.robot.web.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * NATS request-reply client that communicates with the agent-runtime
 * (QwenPaw) to generate UNTRUSTED mission plan proposals.
 * <p>
 * Sends a {@link MissionContextDto} as JSON to the subject
 * {@code opengeobot.agent.mission.plan_request} and expects a
 * {@link PlanProposalDto} JSON response on the NATS reply subject.
 * <p>
 * Safety: the returned proposal is always UNTRUSTED. It must be validated
 * by Schema, permission, state machine, resource and safety checks before
 * any execution occurs. This client does NOT call /cmd_vel, motors or SDKs.
 * <p>
 * Only instantiated when {@code opengeobot.nats.enabled=true} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "opengeobot.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentRuntimeNatsClient {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeNatsClient.class);
    private static final String PLAN_REQUEST_SUBJECT = "opengeobot.agent.mission.plan_request";
    private static final String REPLAN_REQUEST_SUBJECT = "opengeobot.agent.mission.replan";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public AgentRuntimeNatsClient(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a mission planning request to the agent-runtime via NATS
     * request-reply and returns the UNTRUSTED plan proposal.
     *
     * @param context the mission context to plan
     * @param timeout maximum time to wait for the agent response
     * @return the UNTRUSTED plan proposal from the agent-runtime
     * @throws ConflictException if NATS is unavailable or the request times out
     */
    public PlanProposalDto planMission(MissionContextDto context, Duration timeout) {
        String traceId = context.traceId() != null ? context.traceId() : "unknown";
        MDC.put("trace_id", traceId);
        try {
            if (!connectionManager.isConnected()) {
                boolean connected = connectionManager.tryConnect();
                if (!connected) {
                    throw new ConflictException(
                            "NATS is not connected; cannot request mission plan from agent-runtime");
                }
            }

            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                throw new ConflictException("NATS connection is null; cannot request mission plan");
            }

            byte[] payload = serializeContext(context);
            Duration effectiveTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;

            log.info("Requesting mission plan from agent-runtime for mission={} robot={}",
                    context.missionId(), context.robotId());

            Message reply = connection.request(PLAN_REQUEST_SUBJECT, payload, effectiveTimeout);
            if (reply == null) {
                throw new ConflictException(
                        "Agent-runtime did not respond within " + effectiveTimeout + " for mission "
                                + context.missionId());
            }

            PlanProposalDto proposal = deserializeProposal(reply.getData());
            log.info("Received plan proposal from agent-runtime: plan={} steps={} confidence={} trusted={}",
                    proposal.planId(),
                    proposal.steps() != null ? proposal.steps().size() : 0,
                    proposal.confidence(),
                    proposal.isTrusted());

            return proposal;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException(
                    "Interrupted while waiting for agent-runtime response for mission " + context.missionId());
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new ConflictException(
                    "Failed to request mission plan from agent-runtime: " + e.getMessage());
        } finally {
            MDC.remove("trace_id");
        }
    }

    /**
     * Sends a replan request to the agent-runtime via NATS request-reply and
     * returns the UNTRUSTED revised plan proposal.
     *
     * @param request the replan context containing failure information
     * @param timeout maximum time to wait for the agent response
     * @return the UNTRUSTED revised plan proposal from the agent-runtime
     * @throws ConflictException if NATS is unavailable or the request times out
     */
    public PlanProposalDto replanMission(ReplanRequestDto request, Duration timeout) {
        String traceId = request.traceId() != null ? request.traceId() : "unknown";
        MDC.put("trace_id", traceId);
        try {
            if (!connectionManager.isConnected()) {
                boolean connected = connectionManager.tryConnect();
                if (!connected) {
                    throw new ConflictException(
                            "NATS is not connected; cannot request mission replan from agent-runtime");
                }
            }

            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                throw new ConflictException("NATS connection is null; cannot request mission replan");
            }

            byte[] payload = serializeReplanRequest(request);
            Duration effectiveTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;

            log.info("Requesting mission replan from agent-runtime for mission={} robot={}",
                    request.missionId(), request.robotId());

            Message reply = connection.request(REPLAN_REQUEST_SUBJECT, payload, effectiveTimeout);
            if (reply == null) {
                throw new ConflictException(
                        "Agent-runtime did not respond within " + effectiveTimeout + " for replan of mission "
                                + request.missionId());
            }

            PlanProposalDto proposal = deserializeProposal(reply.getData());
            log.info("Received replan proposal from agent-runtime: plan={} steps={} confidence={} trusted={}",
                    proposal.planId(),
                    proposal.steps() != null ? proposal.steps().size() : 0,
                    proposal.confidence(),
                    proposal.isTrusted());

            return proposal;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException(
                    "Interrupted while waiting for agent-runtime replan response for mission " + request.missionId());
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new ConflictException(
                    "Failed to request mission replan from agent-runtime: " + e.getMessage());
        } finally {
            MDC.remove("trace_id");
        }
    }

    private byte[] serializeContext(MissionContextDto context) {
        try {
            return objectMapper.writeValueAsBytes(context);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise mission context", e);
        }
    }

    private byte[] serializeReplanRequest(ReplanRequestDto request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise replan request", e);
        }
    }

    private PlanProposalDto deserializeProposal(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, PlanProposalDto.class);
        } catch (Exception e) {
            throw new ConflictException("Failed to deserialise plan proposal from agent-runtime: " + e.getMessage());
        }
    }
}
