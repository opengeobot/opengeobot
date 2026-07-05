/*
 * Function: TaskCase DTO — API response model for task execution cases
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a task execution case. Maps the
 * {@code memory.task_case} entity to the OpenAPI contract {@code TaskCase}
 * schema. Jackson serialises field names in snake_case globally.
 *
 * @param caseId      public identifier (ULID-based, prefixed with {@code tcs_})
 * @param missionId    identifier of the mission the case belongs to
 * @param robotId      identifier of the robot that executed the task
 * @param skillId      identifier of the skill invoked
 * @param result        execution outcome (SUCCESS or FAILURE)
 * @param durationMs   execution duration in milliseconds
 * @param context      additional execution context (jsonb)
 * @param errorMessage  error message when the result is FAILURE
 * @param occurredAt   UTC timestamp when the case occurred
 * @param traceId       end-to-end trace identifier
 */
public record TaskCaseDto(
        String caseId,
        String missionId,
        String robotId,
        String skillId,
        String result,
        Long durationMs,
        Map<String, Object> context,
        String errorMessage,
        OffsetDateTime occurredAt,
        String traceId
) {
}
