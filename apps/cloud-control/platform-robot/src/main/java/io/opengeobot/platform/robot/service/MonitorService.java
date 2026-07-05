/*
 * Function: Monitor service — real-time monitoring queries and manual takeover for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.mission.MissionState;
import io.opengeobot.platform.common.robot.RobotStatus;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Mission;
import io.opengeobot.platform.robot.domain.MissionStep;
import io.opengeobot.platform.robot.domain.Robot;
import io.opengeobot.platform.robot.domain.SafetyState;
import io.opengeobot.platform.robot.dto.MissionMonitorInfo;
import io.opengeobot.platform.robot.dto.MonitorOverview;
import io.opengeobot.platform.robot.dto.RobotMonitorInfo;
import io.opengeobot.platform.robot.dto.UpdateRobotStatusRequest;
import io.opengeobot.platform.robot.repository.MissionRepository;
import io.opengeobot.platform.robot.repository.MissionStepRepository;
import io.opengeobot.platform.robot.repository.RobotRepository;
import io.opengeobot.platform.robot.repository.SafetyStateRepository;
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
 * Application service for real-time monitoring (F-MONITOR-001). Provides
 * read-only snapshots of robot status and mission progress, a fleet-wide
 * dashboard overview, and a manual takeover action that pauses the active
 * mission and transitions the robot to MAINTENANCE. Physical safety is never
 * bypassed: edge Safety Gateway retains final authority.
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);
    private static final String RESOURCE_TYPE = "monitor";
    private static final String STATE_EMERGENCY_STOPPED = "EMERGENCY_STOPPED";
    private static final String STEP_COMPLETED = "COMPLETED";
    private static final String STEP_EXECUTING = "EXECUTING";
    private static final String STATUS_ONLINE = RobotStatus.ONLINE.name();
    private static final String STATUS_BUSY = RobotStatus.BUSY.name();
    private static final String STATUS_ERROR = RobotStatus.ERROR.name();
    private static final String STATUS_MAINTENANCE = RobotStatus.MAINTENANCE.name();

    private final RobotRepository robotRepository;
    private final MissionRepository missionRepository;
    private final MissionStepRepository missionStepRepository;
    private final SafetyStateRepository safetyStateRepository;
    private final RobotService robotService;
    private final MissionService missionService;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;

    public MonitorService(RobotRepository robotRepository,
                          MissionRepository missionRepository,
                          MissionStepRepository missionStepRepository,
                          SafetyStateRepository safetyStateRepository,
                          RobotService robotService,
                          MissionService missionService,
                          AuditService auditService,
                          ActorResolver actorResolver,
                          ClockProvider clockProvider,
                          ObjectMapper objectMapper) {
        this.robotRepository = robotRepository;
        this.missionRepository = missionRepository;
        this.missionStepRepository = missionStepRepository;
        this.safetyStateRepository = safetyStateRepository;
        this.robotService = robotService;
        this.missionService = missionService;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the real-time monitoring snapshot for a single robot, including
     * the currently executing mission id and telemetry extracted from the
     * robot metadata.
     */
    public RobotMonitorInfo getRobotMonitorInfo(String robotId) {
        Robot robot = requireRobot(robotId);
        String currentMissionId = findCurrentMissionId(robotId);
        Map<String, Object> position = extractTelemetry(robot, "position");
        Map<String, Object> battery = extractTelemetry(robot, "battery");
        return new RobotMonitorInfo(
                robot.getRobotId(),
                robot.getName(),
                robot.getStatus(),
                currentMissionId,
                position,
                battery,
                robot.getLastSeenAt()
        );
    }

    /**
     * Returns the real-time progress snapshot for a single mission, computing
     * the current step index, completion percentage and an estimated
     * completion timestamp.
     */
    public MissionMonitorInfo getMissionMonitorInfo(String missionId) {
        Mission mission = requireMission(missionId);
        List<MissionStep> steps = missionStepRepository.selectByMissionId(missionId);
        int totalSteps = steps.size();
        long completedSteps = steps.stream()
                .filter(s -> STEP_COMPLETED.equals(s.getStatus()))
                .count();
        int currentStep = computeCurrentStep(mission, steps, completedSteps);
        int progressPercent = computeProgressPercent(mission, completedSteps, totalSteps);
        OffsetDateTime eta = computeEta(mission, steps, completedSteps);
        return new MissionMonitorInfo(
                mission.getMissionId(),
                mission.getName(),
                mission.getRobotId(),
                mission.getStatus(),
                currentStep,
                totalSteps,
                progressPercent,
                mission.getStartedAt(),
                eta
        );
    }

    /**
     * Returns the fleet-wide dashboard overview aggregating robot, mission and
     * safety counts.
     */
    public MonitorOverview getOverview() {
        long totalRobots = robotRepository.selectCount(null);
        long onlineRobots = countRobotsByStatus(STATUS_ONLINE);
        long busyRobots = countRobotsByStatus(STATUS_BUSY);
        long errorRobots = countRobotsByStatus(STATUS_ERROR);
        long activeMissions = countActiveMissions();
        long safetyAlerts = countSafetyAlerts();
        return new MonitorOverview(
                totalRobots,
                onlineRobots,
                busyRobots,
                activeMissions,
                errorRobots,
                safetyAlerts
        );
    }

    /**
     * Manually takes over a robot. Any executing mission is paused, the robot
     * is transitioned to MAINTENANCE, and the action is recorded in the audit
     * trail. Edge safety always has final authority.
     */
    @Transactional
    public RobotMonitorInfo takeover(String robotId, String reason) {
        Robot robot = requireRobot(robotId);
        String actor = actorResolver.currentActor();
        String traceId = actorResolver.currentTraceId();
        Instant now = Instant.now(clockProvider.getClock());
        String payloadBefore = toJson(robot);

        pauseActiveMissionIfExists(robotId, reason);

        if (!STATUS_MAINTENANCE.equals(robot.getStatus())) {
            robotService.updateStatus(robotId, new UpdateRobotStatusRequest(STATUS_MAINTENANCE, reason));
        }

        auditTakeover(robotId, actor, traceId, now, reason, payloadBefore);
        log.info("Manual takeover of robot {} by {} (reason: {})", robotId, actor, reason);
        return getRobotMonitorInfo(robotId);
    }

    // ----- helpers -----

    private Robot requireRobot(String robotId) {
        Robot robot = robotRepository.findByRobotId(robotId);
        if (robot == null) {
            throw new ResourceNotFoundException("Robot '" + robotId + "' not found");
        }
        return robot;
    }

    private Mission requireMission(String missionId) {
        LambdaQueryWrapper<Mission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Mission::getMissionId, missionId);
        Mission mission = missionRepository.selectOne(wrapper);
        if (mission == null) {
            throw new ResourceNotFoundException("Mission '" + missionId + "' not found");
        }
        return mission;
    }

    /**
     * Returns the mission id currently executing on the robot, or null when
     * the robot is idle.
     */
    private String findCurrentMissionId(String robotId) {
        List<Mission> active = missionRepository.selectActiveByRobotId(robotId);
        if (active.isEmpty()) {
            return null;
        }
        return active.get(0).getMissionId();
    }

    /**
     * Extracts a structured telemetry sub-map (e.g. position, battery) from
     * the robot metadata jsonb column.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTelemetry(Robot robot, String key) {
        Map<String, Object> metadata = robot.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private int computeCurrentStep(Mission mission, List<MissionStep> steps, long completedSteps) {
        if (steps.isEmpty()) {
            return 0;
        }
        MissionState state = MissionState.valueOf(mission.getStatus());
        if (state == MissionState.EXECUTING) {
            for (MissionStep step : steps) {
                if (STEP_EXECUTING.equals(step.getStatus())) {
                    return step.getStepOrder() != null ? step.getStepOrder() : (int) completedSteps + 1;
                }
            }
            return (int) Math.min(completedSteps + 1, steps.size());
        }
        if (state == MissionState.COMPLETED) {
            return steps.size();
        }
        return 0;
    }

    private int computeProgressPercent(Mission mission, long completedSteps, int totalSteps) {
        if (totalSteps == 0) {
            return 0;
        }
        if (MissionState.COMPLETED.name().equals(mission.getStatus())) {
            return 100;
        }
        return (int) (completedSteps * 100 / totalSteps);
    }

    /**
     * Estimates the completion timestamp from the average duration of the
     * completed steps. Returns null when the mission has not started or there
     * is insufficient data.
     */
    private OffsetDateTime computeEta(Mission mission, List<MissionStep> steps, long completedSteps) {
        if (mission.getStartedAt() == null) {
            return null;
        }
        MissionState state = MissionState.valueOf(mission.getStatus());
        if (state.isTerminal() || state != MissionState.EXECUTING) {
            return null;
        }
        long remaining = steps.size() - completedSteps;
        if (remaining <= 0) {
            return null;
        }
        Duration totalCompleted = Duration.ZERO;
        long counted = 0;
        for (MissionStep step : steps) {
            if (STEP_COMPLETED.equals(step.getStatus())
                    && step.getStartedAt() != null && step.getCompletedAt() != null) {
                totalCompleted = totalCompleted.plus(Duration.between(step.getStartedAt(), step.getCompletedAt()));
                counted++;
            }
        }
        if (counted == 0) {
            return null;
        }
        Duration avgStep = totalCompleted.dividedBy(counted);
        Instant eta = Instant.now(clockProvider.getClock()).plus(avgStep.multipliedBy(remaining));
        return eta.atOffset(ZoneOffset.UTC);
    }

    private long countRobotsByStatus(String status) {
        LambdaQueryWrapper<Robot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Robot::getStatus, status);
        return robotRepository.selectCount(wrapper);
    }

    private long countActiveMissions() {
        QueryWrapper<Mission> wrapper = new QueryWrapper<>();
        wrapper.in("status", MissionState.EXECUTING.name(), MissionState.PAUSED.name());
        return missionRepository.selectCount(wrapper);
    }

    private long countSafetyAlerts() {
        LambdaQueryWrapper<SafetyState> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SafetyState::getState, STATE_EMERGENCY_STOPPED);
        return safetyStateRepository.selectCount(wrapper);
    }

    /**
     * Pauses any executing mission on the robot. A mission that is already
     * PAUSED is left as-is, since the robot is being taken over regardless.
     */
    private void pauseActiveMissionIfExists(String robotId, String reason) {
        List<Mission> active = missionRepository.selectActiveByRobotId(robotId);
        for (Mission mission : active) {
            if (MissionState.EXECUTING.name().equals(mission.getStatus())) {
                try {
                    missionService.pause(mission.getMissionId());
                    log.info("Paused mission {} during takeover of robot {}", mission.getMissionId(), robotId);
                } catch (ConflictException e) {
                    log.warn("Could not pause mission {} during takeover: {}", mission.getMissionId(), e.getMessage());
                }
            }
        }
    }

    private void auditTakeover(String robotId, String actor, String traceId, Instant now,
                              String reason, String payloadBefore) {
        AuditEvent event = new AuditEvent(
                "user",
                actor,
                "monitor.takeover",
                RESOURCE_TYPE,
                robotId,
                "SUCCESS",
                reason,
                null,
                null,
                traceId,
                null,
                now,
                payloadBefore,
                null
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
}
