/*
 * Function: Ops REST controller — endpoints for F-OPS-001 operations dashboard
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.robot.dto.CapacityForecast;
import io.opengeobot.platform.robot.dto.HealthCheckDto;
import io.opengeobot.platform.robot.dto.MetricSnapshotDto;
import io.opengeobot.platform.robot.dto.OpsDashboard;
import io.opengeobot.platform.robot.dto.ReportRecordDto;
import io.opengeobot.platform.robot.service.OpsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * REST controller exposing the operations dashboard API for F-OPS-001. All
 * endpoints are prefixed with {@code /api/v1/ops}. Read endpoints are
 * delegated to {@link OpsService}; the report generation endpoint is a
 * transactional write that persists the generated report.
 */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/dashboard")
    public OpsDashboard getDashboard() {
        return opsService.getDashboard();
    }

    @GetMapping("/metrics")
    public List<MetricSnapshotDto> queryMetrics(
            @RequestParam(name = "metric_name", required = false) String metricName,
            @RequestParam(name = "start", required = false) OffsetDateTime start,
            @RequestParam(name = "end", required = false) OffsetDateTime end,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return opsService.queryMetrics(metricName, start, end, limit);
    }

    @GetMapping("/health")
    public List<HealthCheckDto> getHealth() {
        return opsService.getHealth();
    }

    @GetMapping("/reports/{reportType}")
    public ReportRecordDto generateReport(@PathVariable String reportType) {
        return opsService.generateReport(reportType);
    }

    @GetMapping("/capacity")
    public List<CapacityForecast> getCapacity() {
        return opsService.getCapacity();
    }
}
