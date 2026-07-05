/*
 * Function: FleetService — multi-robot scheduling, conflict detection, failover
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.domain.ConflictRecord;
import io.opengeobot.platform.robot.domain.FailoverEvent;
import io.opengeobot.platform.robot.domain.FleetSchedule;
import io.opengeobot.platform.robot.dto.ConflictRecordDto;
import io.opengeobot.platform.robot.dto.FailoverEventDto;
import io.opengeobot.platform.robot.dto.FleetScheduleDto;
import io.opengeobot.platform.robot.repository.ConflictRecordRepository;
import io.opengeobot.platform.robot.repository.FailoverEventRepository;
import io.opengeobot.platform.robot.repository.FleetScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class FleetService {

    private static final Logger log = LoggerFactory.getLogger(FleetService.class);

    private final FleetScheduleRepository scheduleRepository;
    private final ConflictRecordRepository conflictRepository;
    private final FailoverEventRepository failoverRepository;

    public FleetService(FleetScheduleRepository scheduleRepository,
                        ConflictRecordRepository conflictRepository,
                        FailoverEventRepository failoverRepository) {
        this.scheduleRepository = scheduleRepository;
        this.conflictRepository = conflictRepository;
        this.failoverRepository = failoverRepository;
    }

    public PageResult<FleetScheduleDto> listSchedules(PageRequest pageRequest, String status) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FleetSchedule>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(FleetSchedule::getStatus, status);
        }
        wrapper.orderByDesc(FleetSchedule::getCreatedAt);
        var page = scheduleRepository.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageRequest.pageNumber(), pageRequest.pageSize()),
                wrapper);
        var items = page.getRecords().stream().map(this::toDto).toList();
        return new PageResult<>(items, page.getTotal(), pageRequest.pageNumber(), pageRequest.pageSize());
    }

    @Transactional
    public FleetScheduleDto createSchedule(String missionId, String robotId,
                                            OffsetDateTime plannedStart, OffsetDateTime plannedEnd,
                                            String priority) {
        var schedule = new FleetSchedule();
        schedule.setScheduleId("sch_" + System.currentTimeMillis());
        schedule.setMissionId(missionId);
        schedule.setRobotId(robotId);
        schedule.setPlannedStart(plannedStart);
        schedule.setPlannedEnd(plannedEnd);
        schedule.setPriority(priority != null ? priority : "NORMAL");
        schedule.setStatus("PENDING");
        schedule.setCreatedAt(OffsetDateTime.now());
        schedule.setUpdatedAt(OffsetDateTime.now());
        scheduleRepository.insert(schedule);
        log.info("Created fleet schedule {} for robot {} mission {}", schedule.getScheduleId(), robotId, missionId);
        return toDto(schedule);
    }

    public PageResult<ConflictRecordDto> listConflicts(PageRequest pageRequest) {
        var page = conflictRepository.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageRequest.pageNumber(), pageRequest.pageSize()),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConflictRecord>().orderByDesc(ConflictRecord::getDetectedAt));
        var items = page.getRecords().stream().map(this::toConflictDto).toList();
        return new PageResult<>(items, page.getTotal(), pageRequest.pageNumber(), pageRequest.pageSize());
    }

    @Transactional
    public void resolveConflict(String conflictId, String resolution) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConflictRecord>()
                .eq(ConflictRecord::getConflictId, conflictId);
        var conflict = conflictRepository.selectOne(wrapper);
        if (conflict == null) {
            throw new IllegalArgumentException("Conflict not found: " + conflictId);
        }
        conflict.setResolution(resolution);
        conflict.setResolvedAt(OffsetDateTime.now());
        conflictRepository.updateById(conflict);
        log.info("Resolved conflict {} with {}", conflictId, resolution);
    }

    public PageResult<FailoverEventDto> listFailovers(PageRequest pageRequest) {
        var page = failoverRepository.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageRequest.pageNumber(), pageRequest.pageSize()),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FailoverEvent>().orderByDesc(FailoverEvent::getOccurredAt));
        var items = page.getRecords().stream().map(this::toFailoverDto).toList();
        return new PageResult<>(items, page.getTotal(), pageRequest.pageNumber(), pageRequest.pageSize());
    }

    @Transactional
    public FailoverEventDto triggerFailover(String robotId, String missionId, String reason, String targetRobotId) {
        var event = new FailoverEvent();
        event.setFailoverId("fov_" + System.currentTimeMillis());
        event.setRobotId(robotId);
        event.setMissionId(missionId);
        event.setReason(reason);
        event.setTargetRobotId(targetRobotId);
        event.setStatus("INITIATED");
        event.setOccurredAt(OffsetDateTime.now());
        failoverRepository.insert(event);
        log.info("Triggered failover {} from robot {} to {} for mission {}", event.getFailoverId(), robotId, targetRobotId, missionId);
        return toFailoverDto(event);
    }

    private FleetScheduleDto toDto(FleetSchedule s) {
        return new FleetScheduleDto(s.getScheduleId(), s.getMissionId(), s.getRobotId(),
                s.getPlannedStart(), s.getPlannedEnd(), s.getPriority(), s.getStatus(), s.getCreatedAt());
    }

    private ConflictRecordDto toConflictDto(ConflictRecord c) {
        return new ConflictRecordDto(c.getConflictId(), List.of(), c.getConflictType(),
                c.getDescription(), c.getDetectedAt(), c.getResolvedAt(), c.getResolution(), "OPEN");
    }

    private FailoverEventDto toFailoverDto(FailoverEvent e) {
        return new FailoverEventDto(e.getFailoverId(), e.getRobotId(), e.getMissionId(),
                e.getReason(), e.getTargetRobotId(), e.getStatus(), e.getOccurredAt());
    }
}
