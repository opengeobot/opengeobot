/*
 * Function: Robot status history entity — maps to robot_registry.robot_status_history table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent robot status history entity backed by the
 * {@code robot_registry.robot_status_history} table. Each row records a
 * single status transition for the SM-ROBOT-001 state machine, including the
 * previous and new status, a machine-readable reason and the trace id that
 * links the transition to logs, metrics and audit records.
 */
@TableName(value = "robot_status_history", schema = "robot_registry")
public class RobotStatusHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String robotId;

    private String oldStatus;

    private String newStatus;

    private String reason;

    private OffsetDateTime occurredAt;

    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
