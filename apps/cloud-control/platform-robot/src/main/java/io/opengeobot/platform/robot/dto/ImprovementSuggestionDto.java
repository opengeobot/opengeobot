/*
 * Function: ImprovementSuggestion DTO — API response model for suggestions
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import java.time.OffsetDateTime;

/**
 * API response DTO for an improvement suggestion. Maps the
 * {@code memory.improvement_suggestion} entity to the OpenAPI contract
 * {@code ImprovementSuggestion} schema. Jackson serialises field names in
 * snake_case globally.
 *
 * @param suggestionId   public identifier (ULID-based, prefixed with {@code imp_})
 * @param caseId          identifier of the originating case
 * @param suggestionText  human-readable improvement suggestion
 * @param confidence      confidence score (0.0 to 1.0)
 * @param status           suggestion lifecycle status
 * @param feedback         optional feedback submitted on the suggestion
 * @param createdAt        UTC timestamp when the suggestion was created
 */
public record ImprovementSuggestionDto(
        String suggestionId,
        String caseId,
        String suggestionText,
        Double confidence,
        String status,
        String feedback,
        OffsetDateTime createdAt
) {
}
