/*
 * Function: Mission context DTO - request payload for QwenPaw mission planning
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * Request payload sent to the agent-runtime (QwenPaw) via NATS request-reply.
 * Field names follow the snake_case platform contract, matching the Python
 * {@code MissionContext} pydantic model on the agent-runtime side.
 *
 * @param missionId   unique mission identifier
 * @param traceId     distributed trace id for cross-service correlation
 * @param robotId     target robot identifier
 * @param objective   natural-language mission objective
 * @param constraints optional constraints (speed limits, zones, etc.)
 * @param requestedAt ISO-8601 timestamp of the planning request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MissionContextDto(
        String missionId,
        String traceId,
        String robotId,
        String objective,
        Map<String, Object> constraints,
        String requestedAt
) {
}
