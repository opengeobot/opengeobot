/*
 * Function: Edge gateway heartbeat request DTO
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Heartbeat payload from an edge gateway. Jackson serialises field names in
 * snake_case globally.
 */
public record EdgeGatewayHeartbeatRequest(
        String runtimeVersion,
        Long sequence
) {
}
