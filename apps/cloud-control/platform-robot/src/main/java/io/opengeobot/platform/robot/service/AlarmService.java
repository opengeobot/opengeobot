/*
 * Function: Alarm service — alarm rule lifecycle, event triggering and acknowledgement
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.mission.MissionState;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.robot.RobotStatus;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.AlarmEvent;
import io.opengeobot.platform.robot.domain.AlarmRule;
import io.opengeobot.platform.robot.domain.NotificationChannel;
import io.opengeobot.platform.robot.domain.Robot;
import io.opengeobot.platform.robot.domain.Mission;
import io.opengeobot.platform.robot.dto.AlarmEventDto;
import io.opengeobot.platform.robot.dto.AlarmRuleDto;
import io.opengeobot.platform.robot.dto.CreateAlarmRuleRequest;
import io.opengeobot.platform.robot.dto.CreateNotificationChannelRequest;
import io.opengeobot.platform.robot.dto.NotificationChannelDto;
import io.opengeobot.platform.robot.dto.UpdateAlarmRuleRequest;
import io.opengeobot.platform.robot.repository.AlarmEventRepository;
import io.opengeobot.platform.robot.repository.AlarmRuleRepository;
import io.opengeobot.platform.robot.repository.MissionRepository;
import io.opengeobot.platform.robot.repository.NotificationChannelRepository;
import io.opengeobot.platform.robot.repository.RobotRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Application service for alarm lifecycle management (F-ALARM-001). Provides
 * alarm rule CRUD, alarm event querying, acknowledgement and resolution, and
 * a periodic rule evaluation entry point. The alarm event status follows the
 * SM-ALARM-001 state machine (ACTIVE → ACKNOWLEDGED → RESOLVED). All
 * mutations emit domain events via the transactional outbox and are recorded
 * in the audit trail.
 */
@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);
    private static final String ALARM_TRIGGERED_EVENT = "alarm.triggered.v1";
    private static final String ALARM_ACKNOWLEDGED_EVENT = "alarm.acknowledged.v1";
    private static final String ALARM_RESOLVED_EVENT = "alarm.resolved.v1";
    private static final String RESOURCE_TYPE_ALARM_RULE = "alarm_rule";
    private static final String RESOURCE_TYPE_ALARM_EVENT = "alarm_event";
    private static final String RESOURCE_TYPE_CHANNEL = "notification_channel";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String SEVERITY_MEDIUM = "MEDIUM";
    private static final String CONDITION_GREATER_THAN = "GREATER_THAN";
    private static final String METRIC_ROBOT_OFFLINE = "robot.offline_duration";
    private static final String METRIC_MISSION_FAILURE_RATE = "mission.failure_rate";

    private final AlarmRuleRepository ruleRepository;
    private final AlarmEventRepository eventRepository;
    private final NotificationChannelRepository channelRepository;
    private final RobotRepository robotRepository;
    private final MissionRepository missionRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public AlarmService(AlarmRuleRepository ruleRepository,
                        AlarmEventRepository eventRepository,
                        NotificationChannelRepository channelRepository,
                        RobotRepository robotRepository,
                        MissionRepository missionRepository,
                        OutboxRepository outboxRepository,
                        AuditService auditService,
                        NotificationService notificationService,
                        ActorResolver actorResolver,
                        ClockProvider clockProvider,
                        PublicIdGenerator idGenerator,
                        ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.channelRepository = channelRepository;
        this.robotRepository = robotRepository;
        this.missionRepository = missionRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    // ----- alarm event queries -----

    public PageResult<AlarmEventDto> list(String status, String severity, String source,
                                          PageRequest pageRequest) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), AlarmEvent::getStatus, status)
                .eq(severity != null && !severity.isBlank(), AlarmEvent::getSeverity, severity)
                .eq(source != null && !source.isBlank(), AlarmEvent::getSource, source)
                .orderByDesc(AlarmEvent::getTriggeredAt);
        Page<AlarmEvent> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<AlarmEvent> result = eventRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(AlarmService::toEventDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    // ----- alarm rule CRUD -----

    @Transactional
    public AlarmRuleDto createRule(CreateAlarmRuleRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        AlarmRule rule = new AlarmRule();
        rule.setRuleId(idGenerator.generate("alr"));
        rule.setName(request.name());
        rule.setSource(request.source());
        rule.setMetric(request.metric());
        rule.setCondition(request.condition());
        rule.setThreshold(request.threshold() != null ? request.threshold() : 0.0);
        rule.setSeverity(request.severity() != null ? request.severity() : SEVERITY_MEDIUM);
        rule.setEnabled(request.enabled() != null ? request.enabled() : Boolean.TRUE);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setCreatedBy(actor);
        ruleRepository.insert(rule);

        audit("alarm.rule.create", RESOURCE_TYPE_ALARM_RULE, rule.getRuleId(), "SUCCESS",
                null, toJson(rule));
        log.info("Created alarm rule {} ({})", rule.getRuleId(), rule.getName());
        return toRuleDto(rule);
    }

    public PageResult<AlarmRuleDto> listRules(String source, Boolean enabled, PageRequest pageRequest) {
        LambdaQueryWrapper<AlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(source != null && !source.isBlank(), AlarmRule::getSource, source)
                .eq(enabled != null, AlarmRule::getEnabled, enabled)
                .orderByDesc(AlarmRule::getCreatedAt);
        Page<AlarmRule> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<AlarmRule> result = ruleRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(AlarmService::toRuleDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public AlarmRuleDto updateRule(String ruleId, UpdateAlarmRuleRequest request) {
        AlarmRule rule = requireRule(ruleId);
        String payloadBefore = toJson(rule);
        if (request.name() != null) {
            rule.setName(request.name());
        }
        if (request.condition() != null) {
            rule.setCondition(request.condition());
        }
        if (request.threshold() != null) {
            rule.setThreshold(request.threshold());
        }
        if (request.severity() != null) {
            rule.setSeverity(request.severity());
        }
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }
        rule.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ruleRepository.updateById(rule);

        audit("alarm.rule.update", RESOURCE_TYPE_ALARM_RULE, rule.getRuleId(), "SUCCESS",
                payloadBefore, toJson(rule));
        log.info("Updated alarm rule {}", rule.getRuleId());
        return toRuleDto(rule);
    }

    // ----- alarm lifecycle -----

    @Transactional
    public AlarmEventDto acknowledge(String alarmId) {
        AlarmEvent event = requireEvent(alarmId);
        if (!STATUS_ACTIVE.equals(event.getStatus())) {
            throw new ConflictException("Alarm '" + alarmId + "' is not in ACTIVE state");
        }
        String payloadBefore = toJson(event);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        event.setStatus(STATUS_ACKNOWLEDGED);
        event.setAcknowledgedBy(actor);
        event.setAcknowledgedAt(now);
        eventRepository.updateById(event);

        writeOutboxEvent(ALARM_ACKNOWLEDGED_EVENT, event, Map.of(
                "acknowledged_by", actor,
                "acknowledged_at", now.toString()
        ));
        audit("alarm.acknowledge", RESOURCE_TYPE_ALARM_EVENT, alarmId, "SUCCESS",
                payloadBefore, toJson(event));
        log.info("Alarm {} acknowledged by {}", alarmId, actor);
        return toEventDto(event);
    }

    @Transactional
    public AlarmEventDto resolve(String alarmId) {
        AlarmEvent event = requireEvent(alarmId);
        if (STATUS_RESOLVED.equals(event.getStatus())) {
            throw new ConflictException("Alarm '" + alarmId + "' is already RESOLVED");
        }
        String payloadBefore = toJson(event);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        event.setStatus(STATUS_RESOLVED);
        event.setResolvedAt(now);
        eventRepository.updateById(event);

        writeOutboxEvent(ALARM_RESOLVED_EVENT, event, Map.of(
                "resolved_by", actor,
                "resolved_at", now.toString()
        ));
        audit("alarm.resolve", RESOURCE_TYPE_ALARM_EVENT, alarmId, "SUCCESS",
                payloadBefore, toJson(event));
        log.info("Alarm {} resolved by {}", alarmId, actor);
        return toEventDto(event);
    }

    // ----- notification channels -----

    public List<NotificationChannelDto> listChannels() {
        List<NotificationChannel> channels = channelRepository.selectList(null);
        return channels.stream().map(AlarmService::toChannelDto).toList();
    }

    @Transactional
    public NotificationChannelDto createChannel(CreateNotificationChannelRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        NotificationChannel channel = new NotificationChannel();
        channel.setChannelId(idGenerator.generate("nch"));
        channel.setName(request.name());
        channel.setType(request.type());
        channel.setConfig(request.config() != null ? request.config() : Map.of());
        channel.setEnabled(request.enabled() != null ? request.enabled() : Boolean.TRUE);
        channel.setCreatedAt(now);
        channelRepository.insert(channel);

        audit("alarm.channel.create", RESOURCE_TYPE_CHANNEL, channel.getChannelId(), "SUCCESS",
                null, toJson(channel));
        log.info("Created notification channel {} ({})", channel.getChannelId(), channel.getName());
        return toChannelDto(channel);
    }

    // ----- rule evaluation -----

    /**
     * Evaluates all enabled alarm rules and triggers alarms for conditions
     * that are met. Called periodically by {@link AlarmEvaluator}. For M5
     * this performs a simple check of robot offline duration and mission
     * failure rate against configured thresholds. Duplicate alarms are
     * suppressed: if an ACTIVE alarm already exists for the same rule and
     * source, a new one is not triggered.
     */
    @Transactional
    public void evaluateRules() {
        LambdaQueryWrapper<AlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmRule::getEnabled, true);
        List<AlarmRule> rules = ruleRepository.selectList(wrapper);
        for (AlarmRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.warn("Failed to evaluate alarm rule {}: {}", rule.getRuleId(), e.getMessage());
            }
        }
    }

    private void evaluateRule(AlarmRule rule) {
        if (METRIC_ROBOT_OFFLINE.equals(rule.getMetric())) {
            evaluateRobotOffline(rule);
        } else if (METRIC_MISSION_FAILURE_RATE.equals(rule.getMetric())) {
            evaluateMissionFailureRate(rule);
        }
    }

    /**
     * Evaluates the robot_offline rule: triggers an alarm for each robot
     * that has been offline longer than the configured threshold.
     */
    private void evaluateRobotOffline(AlarmRule rule) {
        List<Robot> offlineRobots = robotRepository.findByStatus(RobotStatus.OFFLINE.name());
        Instant now = Instant.now(clockProvider.getClock());
        double threshold = rule.getThreshold() != null ? rule.getThreshold() : 0.0;
        for (Robot robot : offlineRobots) {
            if (robot.getLastSeenAt() == null) {
                continue;
            }
            long offlineSeconds = Duration.between(robot.getLastSeenAt().toInstant(), now).getSeconds();
            if (offlineSeconds > threshold) {
                String source = robot.getRobotId();
                if (!hasActiveAlarm(rule.getRuleId(), source)) {
                    String message = String.format(
                            "Robot %s has been offline for %ds (threshold: %.0fs)",
                            robot.getRobotId(), offlineSeconds, threshold);
                    triggerAlarm(rule.getRuleId(), source, rule.getSeverity(), message);
                }
            }
        }
    }

    /**
     * Evaluates the mission_failure_rate rule: triggers an alarm if the
     * ratio of failed missions to total missions exceeds the threshold.
     * For M5 this checks the lifetime failure rate.
     */
    private void evaluateMissionFailureRate(AlarmRule rule) {
        long totalMissions = missionRepository.selectCount(null);
        long failedMissions = countFailedMissions();
        if (totalMissions == 0) {
            return;
        }
        double failureRate = (double) failedMissions / totalMissions;
        double threshold = rule.getThreshold() != null ? rule.getThreshold() : 0.0;
        if (failureRate > threshold) {
            String source = "mission";
            if (!hasActiveAlarm(rule.getRuleId(), source)) {
                String message = String.format(
                        "Mission failure rate %.2f%% exceeds threshold %.2f%% (%d/%d failed)",
                        failureRate * 100, threshold * 100, failedMissions, totalMissions);
                triggerAlarm(rule.getRuleId(), source, rule.getSeverity(), message);
            }
        }
    }

    /**
     * Triggers a new alarm event, publishes the alarm.triggered.v1 outbox
     * event, and sends notifications to all enabled channels.
     */
    private AlarmEvent triggerAlarm(String ruleId, String source, String severity, String message) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String traceId = actorResolver.currentTraceId();
        AlarmEvent event = new AlarmEvent();
        event.setAlarmId(idGenerator.generate("alm"));
        event.setRuleId(ruleId);
        event.setSource(source);
        event.setSeverity(severity != null ? severity : SEVERITY_MEDIUM);
        event.setMessage(message);
        event.setStatus(STATUS_ACTIVE);
        event.setTriggeredAt(now);
        event.setTraceId(traceId);
        eventRepository.insert(event);

        writeOutboxEvent(ALARM_TRIGGERED_EVENT, event, Map.of());
        notificationService.sendNotifications(event);
        log.warn("Alarm triggered: {} (rule: {}, source: {}, severity: {})",
                event.getAlarmId(), ruleId, source, severity);
        return event;
    }

    /**
     * Returns true if an ACTIVE alarm already exists for the given rule
     * and source, to prevent duplicate alarms.
     */
    private boolean hasActiveAlarm(String ruleId, String source) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmEvent::getRuleId, ruleId)
                .eq(AlarmEvent::getSource, source)
                .eq(AlarmEvent::getStatus, STATUS_ACTIVE);
        return eventRepository.selectCount(wrapper) > 0;
    }

    // ----- helpers -----

    private AlarmRule requireRule(String ruleId) {
        LambdaQueryWrapper<AlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmRule::getRuleId, ruleId);
        AlarmRule rule = ruleRepository.selectOne(wrapper);
        if (rule == null) {
            throw new ResourceNotFoundException("Alarm rule '" + ruleId + "' not found");
        }
        return rule;
    }

    private AlarmEvent requireEvent(String alarmId) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmEvent::getAlarmId, alarmId);
        AlarmEvent event = eventRepository.selectOne(wrapper);
        if (event == null) {
            throw new ResourceNotFoundException("Alarm '" + alarmId + "' not found");
        }
        return event;
    }

    private long countFailedMissions() {
        LambdaQueryWrapper<Mission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Mission::getStatus, MissionState.FAILED.name());
        return missionRepository.selectCount(wrapper);
    }

    private void writeOutboxEvent(String eventType, AlarmEvent event, Map<String, Object> extra) {
        Map<String, Object> payload = Map.of(
                "alarm_id", event.getAlarmId(),
                "rule_id", event.getRuleId() != null ? event.getRuleId() : "",
                "source", event.getSource() != null ? event.getSource() : "",
                "severity", event.getSeverity() != null ? event.getSeverity() : "",
                "message", event.getMessage() != null ? event.getMessage() : "",
                "triggered_at", event.getTriggeredAt() != null ? event.getTriggeredAt().toString() : "",
                "trace_id", event.getTraceId() != null ? event.getTraceId() : ""
        );
        OutboxEvent outbox = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                eventType,
                "1",
                RESOURCE_TYPE_ALARM_EVENT,
                event.getAlarmId(),
                0L,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                event.getTraceId(),
                false,
                null,
                0
        );
        outboxRepository.save(outbox);
    }

    private void audit(String action, String resourceType, String resourceId, String result,
                       String payloadBefore, String payloadAfter) {
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
                payloadBefore,
                payloadAfter
        );
        auditService.record(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise value to JSON for audit", e);
            return null;
        }
    }

    private static AlarmRuleDto toRuleDto(AlarmRule entity) {
        return new AlarmRuleDto(
                entity.getRuleId(),
                entity.getName(),
                entity.getSource(),
                entity.getMetric(),
                entity.getCondition(),
                entity.getThreshold(),
                entity.getSeverity(),
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy()
        );
    }

    private static AlarmEventDto toEventDto(AlarmEvent entity) {
        return new AlarmEventDto(
                entity.getAlarmId(),
                entity.getRuleId(),
                entity.getSource(),
                entity.getSeverity(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getTriggeredAt(),
                entity.getAcknowledgedBy(),
                entity.getAcknowledgedAt(),
                entity.getResolvedAt(),
                entity.getTraceId()
        );
    }

    private static NotificationChannelDto toChannelDto(NotificationChannel entity) {
        return new NotificationChannelDto(
                entity.getChannelId(),
                entity.getName(),
                entity.getType(),
                entity.getConfig(),
                entity.getEnabled(),
                entity.getCreatedAt()
        );
    }
}
