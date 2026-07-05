/*
 * Function: MetricSnapshot DTO — API response model for metric data points
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API response DTO for a metric data point. Maps the
 * {@code ops.metric_snapshot} entity to the OpenAPI contract
 * {@code MetricSnapshot} schema.
 *
 * @param metricName name of the metric
 * @param value      numeric value of the metric
 * @param unit       unit of the metric value
 * @param tags       optional tags for dimensional filtering
 * @param timestamp  UTC timestamp when the metric was recorded
 */
public record MetricSnapshotDto(
        @JsonProperty("metric_name") String metricName,
        Double value,
        String unit,
        Map<String, Object> tags,
        OffsetDateTime timestamp
) {
}
