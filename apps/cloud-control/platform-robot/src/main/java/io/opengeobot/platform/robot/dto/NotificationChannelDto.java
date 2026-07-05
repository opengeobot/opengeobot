/*
 * Function: NotificationChannel DTO — API response model for notification channels
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a notification channel. Maps the
 * {@code alarm.notification_channel} entity to the OpenAPI contract
 * {@code NotificationChannel} schema.
 *
 * @param channelId unique channel identifier
 * @param name      human-readable channel name
 * @param type      delivery type (in-app, webhook, email)
 * @param config    channel-specific configuration
 * @param enabled   whether the channel is active
 * @param createdAt UTC timestamp when the channel was created
 */
public record NotificationChannelDto(
        @JsonProperty("channel_id") String channelId,
        String name,
        String type,
        Map<String, Object> config,
        Boolean enabled,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
}
