/*
 * Function: Create export request DTO
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request body for creating an asynchronous data export task. The
 * {@code filter} is an opaque JSON object interpreted by the
 * {@code resourceType} handler.
 */
public record CreateExportRequest(
        @NotBlank(message = "resource_type must not be blank")
        String resourceType,

        @NotBlank(message = "format must not be blank")
        String format,

        Map<String, Object> filter
) {
}
