/*
 * Function: SafetyState entity — maps to policy.safety_state table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent safety state entity backed by the {@code policy.safety_state}
 * table. The {@code state} field follows the SM-SAFETY-001 state machine
 * (NORMAL → EMERGENCY_STOPPED → RESETTING → NORMAL). Each robot has at most
 * one row; when the state is {@code EMERGENCY_STOPPED} all physical skill
 * execution must be blocked.
 */
@TableName(value = "safety_state", schema = "policy")
public class SafetyState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String robotId;

    private String state;

    private OffsetDateTime lastEventAt;

    private String reason;

    private OffsetDateTime updatedAt;

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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public OffsetDateTime getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(OffsetDateTime lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
