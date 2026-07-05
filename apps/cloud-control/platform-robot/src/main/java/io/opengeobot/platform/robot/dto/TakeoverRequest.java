/*
 * Function: Takeover request DTO — manual robot takeover request body for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for manually taking over a robot. The takeover pauses any
 * active mission, transitions the robot to MAINTENANCE and records an audit
 * entry with the supplied reason. Edge safety always has final authority.
 */
public record TakeoverRequest(
        @JsonProperty("reason") String reason
) {
}
