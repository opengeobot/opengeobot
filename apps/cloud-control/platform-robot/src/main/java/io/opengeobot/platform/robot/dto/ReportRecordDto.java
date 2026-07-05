/*
 * Function: ReportRecord DTO — API response model for operations reports
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for an operations report record. Maps the
 * {@code ops.report_record} entity to the OpenAPI contract
 * {@code ReportRecord} schema.
 *
 * @param reportType  type of the report (daily, weekly, monthly)
 * @param periodStart start of the report period (inclusive)
 * @param periodEnd   end of the report period (exclusive)
 * @param summary     aggregated summary data for the period
 * @param generatedAt UTC timestamp when the report was generated
 */
public record ReportRecordDto(
        @JsonProperty("report_type") String reportType,
        @JsonProperty("period_start") OffsetDateTime periodStart,
        @JsonProperty("period_end") OffsetDateTime periodEnd,
        Map<String, Object> summary,
        @JsonProperty("generated_at") OffsetDateTime generatedAt
) {
}
