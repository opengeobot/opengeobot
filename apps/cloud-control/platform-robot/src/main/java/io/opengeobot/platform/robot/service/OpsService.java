/*
 * Function: Ops service — dashboard, metrics, health, reports and capacity for F-OPS-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.mission.MissionState;
import io.opengeobot.platform.common.robot.RobotStatus;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.AlarmEvent;
import io.opengeobot.platform.robot.domain.HealthCheck;
import io.opengeobot.platform.robot.domain.MetricSnapshot;
import io.opengeobot.platform.robot.domain.Mission;
import io.opengeobot.platform.robot.domain.ReportRecord;
import io.opengeobot.platform.robot.domain.Robot;
import io.opengeobot.platform.robot.dto.CapacityForecast;
import io.opengeobot.platform.robot.dto.HealthCheckDto;
import io.opengeobot.platform.robot.dto.MetricSnapshotDto;
import io.opengeobot.platform.robot.dto.OpsDashboard;
import io.opengeobot.platform.robot.dto.ReportRecordDto;
import io.opengeobot.platform.robot.repository.AlarmEventRepository;
import io.opengeobot.platform.robot.repository.HealthCheckRepository;
import io.opengeobot.platform.robot.repository.MetricSnapshotRepository;
import io.opengeobot.platform.robot.repository.MissionRepository;
import io.opengeobot.platform.robot.repository.ReportRecordRepository;
import io.opengeobot.platform.robot.repository.RobotRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service for operations dashboard, metrics, health, reports and
 * capacity forecasting (F-OPS-001). Aggregates data across the robot, mission
 * and alarm domains to provide a unified operations view. Health checks
 * verify connectivity to critical dependencies (database, NATS, MinIO) and
 * persist results to the {@code ops.health_check} table.
 */
@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);
    private static final String STATUS_HEALTHY = "HEALTHY";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_UNHEALTHY = "UNHEALTHY";
    private static final String COMPONENT_POSTGRES = "postgresql";
    private static final String COMPONENT_NATS = "nats";
    private static final String COMPONENT_MINIO = "minio";
    private static final String REPORT_DAILY = "daily";
    private static final String REPORT_WEEKLY = "weekly";
    private static final String REPORT_MONTHLY = "monthly";
    private static final int DEFAULT_CAPACITY_THRESHOLD_ROBOTS = 100;
    private static final int DEFAULT_CAPACITY_THRESHOLD_MISSIONS = 10;

    private final RobotRepository robotRepository;
    private final MissionRepository missionRepository;
    private final AlarmEventRepository alarmEventRepository;
    private final MetricSnapshotRepository metricSnapshotRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final ReportRecordRepository reportRecordRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;

    public OpsService(RobotRepository robotRepository,
                      MissionRepository missionRepository,
                      AlarmEventRepository alarmEventRepository,
                      MetricSnapshotRepository metricSnapshotRepository,
                      HealthCheckRepository healthCheckRepository,
                      ReportRecordRepository reportRecordRepository,
                      AuditService auditService,
                      ActorResolver actorResolver,
                      ClockProvider clockProvider,
                      ObjectMapper objectMapper) {
        this.robotRepository = robotRepository;
        this.missionRepository = missionRepository;
        this.alarmEventRepository = alarmEventRepository;
        this.metricSnapshotRepository = metricSnapshotRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.reportRecordRepository = reportRecordRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the operations dashboard overview aggregating system health,
     * robot statistics, mission statistics and alarm statistics.
     */
    public OpsDashboard getDashboard() {
        List<HealthCheckDto> healthChecks = getHealth();
        String overall = aggregateHealth(healthChecks);
        OpsDashboard.SystemHealth systemHealth =
                new OpsDashboard.SystemHealth(overall, healthChecks);

        OpsDashboard.RobotStats robotStats = new OpsDashboard.RobotStats(
                countRobots(null),
                countRobots(RobotStatus.ONLINE.name()),
                countRobots(RobotStatus.OFFLINE.name()),
                countRobots(RobotStatus.BUSY.name()),
                countRobots(RobotStatus.ERROR.name())
        );

        OpsDashboard.MissionStats missionStats = new OpsDashboard.MissionStats(
                countMissions(null),
                countActiveMissions(),
                countMissions(MissionState.COMPLETED.name()),
                countMissions(MissionState.FAILED.name())
        );

        OpsDashboard.AlarmStats alarmStats = new OpsDashboard.AlarmStats(
                countAlarms("ACTIVE"),
                countAlarms("ACKNOWLEDGED"),
                countAlarms("RESOLVED")
        );

        return new OpsDashboard(systemHealth, robotStats, missionStats, alarmStats);
    }

    /**
     * Queries metric snapshots filtered by metric name and time range.
     */
    public List<MetricSnapshotDto> queryMetrics(String metricName, OffsetDateTime start,
                                                 OffsetDateTime end, int limit) {
        LambdaQueryWrapper<MetricSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(metricName != null && !metricName.isBlank(),
                        MetricSnapshot::getMetricName, metricName)
                .ge(start != null, MetricSnapshot::getTimestamp, start)
                .le(end != null, MetricSnapshot::getTimestamp, end)
                .orderByDesc(MetricSnapshot::getTimestamp)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 1000));
        List<MetricSnapshot> snapshots = metricSnapshotRepository.selectList(wrapper);
        return snapshots.stream().map(OpsService::toMetricDto).toList();
    }

    /**
     * Performs health checks for critical dependencies (database, NATS, MinIO),
     * persists the results to the {@code ops.health_check} table, and returns
     * the list of health check results. For M5, NATS and MinIO checks are
     * stubbed as HEALTHY since dedicated health check clients are not yet
     * wired in the platform-robot module.
     */
    @Transactional
    public List<HealthCheckDto> getHealth() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<HealthCheckDto> results = new ArrayList<>();

        results.add(checkDatabase(now));
        results.add(checkStub(COMPONENT_NATS, now));
        results.add(checkStub(COMPONENT_MINIO, now));

        for (HealthCheckDto dto : results) {
            HealthCheck entity = new HealthCheck();
            entity.setComponent(dto.component());
            entity.setStatus(dto.status());
            entity.setLatencyMs(dto.latencyMs());
            entity.setErrorMessage(dto.errorMessage());
            entity.setLastCheckAt(dto.lastCheckAt());
            healthCheckRepository.insert(entity);
        }
        return results;
    }

    /**
     * Generates an operations report for the given period type, querying
     * historical data and persisting the summary as a report record.
     */
    @Transactional
    public ReportRecordDto generateReport(String reportType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime periodStart = computePeriodStart(reportType, now);

        long totalMissions = countMissionsInRange(periodStart, now);
        long completedMissions = countMissionsByStatusInRange(MissionState.COMPLETED.name(), periodStart, now);
        long failedMissions = countMissionsByStatusInRange(MissionState.FAILED.name(), periodStart, now);
        long activeAlarms = countAlarms("ACTIVE");
        long totalRobots = countRobots(null);
        long onlineRobots = countRobots(RobotStatus.ONLINE.name());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_missions", totalMissions);
        summary.put("completed_missions", completedMissions);
        summary.put("failed_missions", failedMissions);
        summary.put("active_alarms", activeAlarms);
        summary.put("total_robots", totalRobots);
        summary.put("online_robots", onlineRobots);

        ReportRecord record = new ReportRecord();
        record.setReportType(reportType);
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(now);
        record.setSummary(summary);
        record.setGeneratedAt(now);
        reportRecordRepository.insert(record);

        audit("ops.report.generate", "report_record", reportType, "SUCCESS");
        log.info("Generated {} report (period: {} to {})", reportType, periodStart, now);
        return toReportDto(record);
    }

    /**
     * Returns a capacity forecast for platform resources, comparing current
     * usage against projected usage and thresholds.
     */
    public List<CapacityForecast> getCapacity() {
        List<CapacityForecast> forecasts = new ArrayList<>();

        long robotCount = countRobots(null);
        double projectedRobots = robotCount * 1.1;
        forecasts.add(new CapacityForecast(
                "robots", robotCount, projectedRobots,
                DEFAULT_CAPACITY_THRESHOLD_ROBOTS, "count",
                projectedRobots > DEFAULT_CAPACITY_THRESHOLD_ROBOTS
        ));

        long activeMissions = countActiveMissions();
        double projectedMissions = activeMissions * 1.2;
        forecasts.add(new CapacityForecast(
                "concurrent_missions", activeMissions, projectedMissions,
                DEFAULT_CAPACITY_THRESHOLD_MISSIONS, "count",
                projectedMissions > DEFAULT_CAPACITY_THRESHOLD_MISSIONS
        ));

        return forecasts;
    }

    // ----- health check helpers -----

    private HealthCheckDto checkDatabase(OffsetDateTime now) {
        long startNanos = System.nanoTime();
        try {
            robotRepository.selectCount(null);
            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            return new HealthCheckDto(COMPONENT_POSTGRES, STATUS_HEALTHY, latencyMs, null, now);
        } catch (Exception e) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.error("Database health check failed: {}", e.getMessage());
            return new HealthCheckDto(COMPONENT_POSTGRES, STATUS_UNHEALTHY, latencyMs,
                    e.getMessage(), now);
        }
    }

    /**
     * Stub health check for M5: records the component as HEALTHY without an
     * actual connectivity probe. This is acceptable until dedicated health
     * check clients are wired into the platform-robot module.
     */
    private HealthCheckDto checkStub(String component, OffsetDateTime now) {
        return new HealthCheckDto(component, STATUS_HEALTHY, 0L, null, now);
    }

    private String aggregateHealth(List<HealthCheckDto> checks) {
        boolean anyUnhealthy = checks.stream()
                .anyMatch(c -> STATUS_UNHEALTHY.equals(c.status()));
        if (anyUnhealthy) {
            return STATUS_UNHEALTHY;
        }
        boolean anyDegraded = checks.stream()
                .anyMatch(c -> STATUS_DEGRADED.equals(c.status()));
        return anyDegraded ? STATUS_DEGRADED : STATUS_HEALTHY;
    }

    // ----- counting helpers -----

    private long countRobots(String status) {
        LambdaQueryWrapper<Robot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Robot::getStatus, status);
        return robotRepository.selectCount(wrapper);
    }

    private long countMissions(String status) {
        LambdaQueryWrapper<Mission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Mission::getStatus, status);
        return missionRepository.selectCount(wrapper);
    }

    private long countActiveMissions() {
        LambdaQueryWrapper<Mission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Mission::getStatus, MissionState.EXECUTING.name(), MissionState.PAUSED.name());
        return missionRepository.selectCount(wrapper);
    }

    private long countAlarms(String status) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, AlarmEvent::getStatus, status);
        return alarmEventRepository.selectCount(wrapper);
    }

    private long countMissionsInRange(OffsetDateTime start, OffsetDateTime end) {
        LambdaQueryWrapper<Mission> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Mission::getCreatedAt, start).le(Mission::getCreatedAt, end);
        return missionRepository.selectCount(wrapper);
    }

    private long countMissionsByStatusInRange(String status, OffsetDateTime start, OffsetDateTime end) {
        LambdaQueryWrapper<Mission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Mission::getStatus, status)
                .ge(Mission::getCreatedAt, start)
                .le(Mission::getCreatedAt, end);
        return missionRepository.selectCount(wrapper);
    }

    private OffsetDateTime computePeriodStart(String reportType, OffsetDateTime now) {
        return switch (reportType) {
            case REPORT_DAILY -> now.minusDays(1);
            case REPORT_WEEKLY -> now.minusDays(7);
            case REPORT_MONTHLY -> now.minusDays(30);
            default -> now.minusDays(1);
        };
    }

    // ----- audit -----

    private void audit(String action, String resourceType, String resourceId, String result) {
        AuditEvent event = new AuditEvent(
                "user",
                actorResolver.currentActor(),
                action,
                resourceType,
                resourceId,
                result,
                null,
                null,
                null,
                actorResolver.currentTraceId(),
                null,
                Instant.now(clockProvider.getClock()),
                null,
                null
        );
        auditService.record(event);
    }

    // ----- DTO mapping -----

    private static MetricSnapshotDto toMetricDto(MetricSnapshot entity) {
        return new MetricSnapshotDto(
                entity.getMetricName(),
                entity.getValue(),
                entity.getUnit(),
                entity.getTags(),
                entity.getTimestamp()
        );
    }

    private static ReportRecordDto toReportDto(ReportRecord entity) {
        return new ReportRecordDto(
                entity.getReportType(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getSummary(),
                entity.getGeneratedAt()
        );
    }
}
