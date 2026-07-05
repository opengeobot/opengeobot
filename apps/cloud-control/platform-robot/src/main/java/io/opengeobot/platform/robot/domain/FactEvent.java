/*
 * Function: FactEvent entity — maps to trace.fact_event table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.robot.config.JsonbMapTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Immutable fact event entity backed by the {@code trace.fact_event} table.
 * Fact events capture discrete occurrences within a trace for replay and
 * audit purposes. The {@code payload} is stored as jsonb. The
 * {@code traceId} links this event to the trace context.
 */
@TableName(value = "fact_event", schema = "trace", autoResultMap = true)
public class FactEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private String eventType;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> payload;

    private OffsetDateTime occurredAt;

    private String actorId;

    private String robotId;

    private String missionId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
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
}
