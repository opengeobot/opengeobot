/*
 * Function: FailoverEvent entity — maps to fleet.failover_event for F-FLEET-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent failover event entity backed by the {@code fleet.failover_event}
 * table. Records the transfer of a mission from one robot to another when the
 * original robot becomes unavailable. The {@code status} field tracks the
 * failover lifecycle (INITIATED, COMPLETED, FAILED).
 */
@TableName(value = "failover_event", schema = "fleet")
public class FailoverEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String failoverId;

    private String robotId;

    private String missionId;

    private String reason;

    private String targetRobotId;

    private String status;

    private OffsetDateTime occurredAt;

    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFailoverId() {
        return failoverId;
    }

    public void setFailoverId(String failoverId) {
        this.failoverId = failoverId;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTargetRobotId() {
        return targetRobotId;
    }

    public void setTargetRobotId(String targetRobotId) {
        this.targetRobotId = targetRobotId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
