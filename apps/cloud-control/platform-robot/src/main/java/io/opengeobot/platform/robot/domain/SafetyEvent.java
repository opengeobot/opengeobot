/*
 * Function: SafetyEvent entity — maps to policy.safety_event table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Immutable safety event entity backed by the {@code policy.safety_event}
 * table. Events are append-only and carry a {@code traceId} linking them to
 * the end-to-end trace context. The {@code eventType} field is a code
 * contract (EMERGENCY_STOP, RESET, SAFETY_CHECK_PASSED, SAFETY_CHECK_FAILED).
 */
@TableName(value = "safety_event", schema = "policy")
public class SafetyEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String robotId;

    private String eventType;

    private String reason;

    private OffsetDateTime occurredAt;

    private String traceId;

    private String actorId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
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

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }
}
