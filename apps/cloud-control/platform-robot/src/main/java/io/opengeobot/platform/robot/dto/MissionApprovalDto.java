/*
 * Function: Mission approval DTO — F-MISSION-002 response shape
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Response shape for a mission approval record. The {@code status} field is a
 * code contract (PENDING, APPROVED, REJECTED). Field names follow the
 * snake_case platform contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MissionApprovalDto(
        @JsonProperty("mission_id") String missionId,
        @JsonProperty("approver_id") String approverId,
        String status,
        String comment,
        @JsonProperty("approved_at") OffsetDateTime approvedAt,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
}
