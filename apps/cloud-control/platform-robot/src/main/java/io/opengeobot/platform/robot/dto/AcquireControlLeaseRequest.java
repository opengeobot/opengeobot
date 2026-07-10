/*
 * Function: Acquire control lease request DTO
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for acquiring a robot control lease. Jackson serialises field
 * names in snake_case globally.
 */
public record AcquireControlLeaseRequest(
        @Min(value = 30, message = "ttl_seconds must be at least 30")
        @Max(value = 3600, message = "ttl_seconds must be at most 3600")
        Integer ttlSeconds,

        String reason
) {
}
