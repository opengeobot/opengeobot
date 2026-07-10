/*
 * Function: FeedbackRequest DTO — request body for submitting suggestion feedback
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for submitting feedback on an improvement suggestion. Jackson
 * serialises field names in snake_case globally.
 * {@code decision} must be ACCEPT or REJECT; accepted suggestions never
 * auto-apply motion changes — they remain ACCEPTED pending human rollout.
 */
public record FeedbackRequest(
        @NotBlank(message = "suggestion_id must not be blank")
        String suggestionId,

        @NotBlank(message = "feedback must not be blank")
        String feedback,

        String decision
) {
}
