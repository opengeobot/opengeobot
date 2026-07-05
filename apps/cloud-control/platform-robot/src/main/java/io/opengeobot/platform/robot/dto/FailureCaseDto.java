/*
 * Function: FailureCase DTO — API response model for analysed failure cases
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.util.List;
import java.util.Map;

/**
 * API response DTO for a failure case. Maps the {@code memory.failure_case}
 * entity to the OpenAPI contract {@code FailureCase} schema. Jackson
 * serialises field names in snake_case globally.
 *
 * @param caseId         identifier of the originating task case
 * @param failureType     categorised failure type
 * @param rootCause       root cause analysis summary
 * @param environment     environmental context at the time of failure (jsonb)
 * @param similarCases    identifiers of similar historical cases
 */
public record FailureCaseDto(
        String caseId,
        String failureType,
        String rootCause,
        Map<String, Object> environment,
        List<String> similarCases
) {
}
