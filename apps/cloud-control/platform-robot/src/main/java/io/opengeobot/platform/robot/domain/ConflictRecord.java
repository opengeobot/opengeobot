/*
 * Function: ConflictRecord entity — maps to fleet.conflict_record for F-FLEET-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.robot.config.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

/**
 * Persistent conflict record entity backed by the
 * {@code fleet.conflict_record} table. Records a detected conflict between two
 * or more fleet schedules. The {@code scheduleIds} field stores a JSON array of
 * schedule identifiers as jsonb. The {@code status} field tracks whether the
 * conflict is {@code OPEN} or {@code RESOLVED}.
 */
@TableName(value = "conflict_record", schema = "fleet", autoResultMap = true)
public class ConflictRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conflictId;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String scheduleIds;

    private String conflictType;

    private String description;

    private OffsetDateTime detectedAt;

    private OffsetDateTime resolvedAt;

    private String resolution;

    private String status;

    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConflictId() {
        return conflictId;
    }

    public void setConflictId(String conflictId) {
        this.conflictId = conflictId;
    }

    public String getScheduleIds() {
        return scheduleIds;
    }

    public void setScheduleIds(String scheduleIds) {
        this.scheduleIds = scheduleIds;
    }

    public String getConflictType() {
        return conflictType;
    }

    public void setConflictType(String conflictType) {
        this.conflictType = conflictType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(OffsetDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
