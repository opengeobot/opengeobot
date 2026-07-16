/*
 * Function: Plan proposal DTO - UNTRUSTED response from QwenPaw agent-runtime
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * UNTRUSTED plan proposal returned by the agent-runtime via NATS request-reply.
 * The {@code isTrusted} field is always {@code false} - the proposal must be
 * validated by Schema, permission, state machine, resource and safety checks
 * before any execution occurs (safety red line #4).
 * <p>
 * Field names follow the snake_case platform contract, matching the Python
 * {@code PlanProposal} pydantic model.
 *
 * @param planId      unique plan identifier
 * @param missionId   mission this plan belongs to
 * @param traceId     distributed trace id
 * @param robotId     target robot identifier
 * @param steps       ordered list of plan steps
 * @param confidence   agent confidence score [0.0, 1.0]
 * @param rationale   agent reasoning explanation
 * @param isTrusted   always false - proposal is untrusted until validated
 * @param error       error message if planning failed; null on success
 * @param generatedAt ISO-8601 timestamp of plan generation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanProposalDto(
        String planId,
        String missionId,
        String traceId,
        String robotId,
        List<PlanStepDto> steps,
        double confidence,
        String rationale,
        boolean isTrusted,
        String error,
        String generatedAt
) {
}
