/*
 * Function: Mission approval DTO and request — F-MISSION-002
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for recording an approval or rejection decision. The
 * {@code comment} is required to explain the decision.
 */
public record ApprovalRequest(
        String comment
) {
}
