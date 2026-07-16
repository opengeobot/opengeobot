/*
 * Function: Replan request DTO - payload sent to agent-runtime for dynamic replanning
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * Request payload sent to the agent-runtime (QwenPaw) via NATS request-reply
 * when a mission step fails and the platform asks the LLM to replan the
 * remaining steps with full failure context.
 * <p>
 * Field names follow the snake_case platform contract, matching the Python
 * {@code ReplanRequest} pydantic model on the agent-runtime side.
 *
 * @param missionId         the mission that needs replanning
 * @param traceId           distributed trace id for cross-service correlation
 * @param robotId           target robot identifier
 * @param originalObjective the original mission objective text
 * @param completedSteps    steps that completed successfully before the failure
 * @param failedStep        the step that failed, including its error
 * @param failureReason     human-readable reason for the step failure
 * @param remainingSteps    steps that had not yet been executed when the failure occurred
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReplanRequestDto(
        String missionId,
        String traceId,
        String robotId,
        String originalObjective,
        List<Map<String, Object>> completedSteps,
        Map<String, Object> failedStep,
        String failureReason,
        List<Map<String, Object>> remainingSteps
) {
}
