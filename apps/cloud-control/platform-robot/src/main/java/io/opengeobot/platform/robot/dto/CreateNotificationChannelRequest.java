/*
 * Function: CreateNotificationChannelRequest DTO — request body for creating notification channels
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request body for creating a notification channel. Field names follow the
 * snake_case platform contract.
 *
 * @param name    human-readable channel name
 * @param type    delivery type (in-app, webhook, email)
 * @param config  channel-specific configuration
 * @param enabled whether the channel is active (defaults to true)
 */
public record CreateNotificationChannelRequest(
        @NotBlank String name,
        @NotBlank String type,
        Map<String, Object> config,
        Boolean enabled
) {
}
