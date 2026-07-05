/*
 * Function: CapacityForecast DTO — resource capacity forecast for F-OPS-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Capacity forecast entry comparing current usage of a resource against
 * projected usage and a threshold.
 *
 * @param resource        name of the resource being forecast
 * @param currentUsage    current usage of the resource
 * @param projectedUsage  projected usage based on recent trends
 * @param threshold       capacity threshold for the resource
 * @param unit            unit of the usage values
 * @param alert           whether projected usage exceeds the threshold
 */
public record CapacityForecast(
        String resource,
        @JsonProperty("current_usage") double currentUsage,
        @JsonProperty("projected_usage") double projectedUsage,
        double threshold,
        String unit,
        boolean alert
) {
}
