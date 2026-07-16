/*
 * Function: Edge command DTO - command dispatched to the edge Safety Gateway
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * Command dispatched to the edge Safety Gateway via NATS JetStream. Commands are
 * published to {@code opengeobot.dev.edge.command.{robotId}} and must pass
 * through the edge Safety Gateway before any physical action is taken (safety
 * red line #2).
 * <p>
 * Field names follow the snake_case platform contract.
 *
 * @param commandId   unique command identifier (idempotency key)
 * @param traceId     distributed trace id for cross-service correlation
 * @param commandType  command type: start_mission, pause, resume, cancel,
 *                     emergency_stop, execute_skill, reset_safety
 * @param missionId   mission this command belongs to (nullable for non-mission commands)
 * @param skillId     skill to execute (only for execute_skill commands)
 * @param params      command parameters
 * @param issuedAt    ISO-8601 timestamp of command issuance
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EdgeCommandDto(
        String commandId,
        String traceId,
        String commandType,
        String missionId,
        String skillId,
        Map<String, Object> params,
        String issuedAt
) {
}
