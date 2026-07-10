/*
 * Function: Revoke edge gateway request DTO
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

/**
 * Optional request body for revoking an edge gateway. Jackson serialises
 * field names in snake_case globally.
 */
public record RevokeEdgeGatewayRequest(
        String reason
) {
}
