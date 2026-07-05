/*
 * Function: FleetSchedule entity — maps to fleet.fleet_schedule for F-FLEET-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent fleet schedule entity backed by the {@code fleet.fleet_schedule}
 * table. A schedule assigns a mission to a robot within a planned time window.
 * The {@code status} field follows the SM-FLEET-001 state machine
 * (PENDING, APPROVED, ACTIVE, COMPLETED, CANCELLED) and is a code contract.
 */
@TableName(value = "fleet_schedule", schema = "fleet")
public class FleetSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scheduleId;

    private String missionId;

    private String robotId;

    private OffsetDateTime plannedStart;

    private OffsetDateTime plannedEnd;

    private String priority;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public OffsetDateTime getPlannedStart() {
        return plannedStart;
    }

    public void setPlannedStart(OffsetDateTime plannedStart) {
        this.plannedStart = plannedStart;
    }

    public OffsetDateTime getPlannedEnd() {
        return plannedEnd;
    }

    public void setPlannedEnd(OffsetDateTime plannedEnd) {
        this.plannedEnd = plannedEnd;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
