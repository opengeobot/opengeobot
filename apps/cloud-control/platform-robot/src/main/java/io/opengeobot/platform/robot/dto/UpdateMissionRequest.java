/*
 * Function: Update mission request DTO — F-MISSION-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Request body for updating mutable mission metadata (name, description,
 * priority, scheduled_at) of a mission that has not yet started executing.
 * All fields are optional; only provided fields are applied.
 */
public record UpdateMissionRequest(
        String name,
        String description,
        String priority,
        @JsonProperty("scheduled_at")
        OffsetDateTime scheduledAt
) {
}
