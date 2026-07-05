/*
 * Function: SkillVersion entity — maps to skill_registry.skill_version table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent skill version entity backed by the
 * {@code skill_registry.skill_version} table. Each publish action appends a
 * new row with an incremented {@code version}. The {@code inputSchema} and
 * {@code outputSchema} at the time of publishing are captured immutably. The
 * table is append-only; rows are never updated except for status transitions
 * (PUBLISHED → DEPRECATED).
 */
@TableName(value = "skill_version", schema = "skill_registry", autoResultMap = true)
public class SkillVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skillId;

    private Integer version;

    private String status;

    private String changelog;

    @TableField(typeHandler = io.opengeobot.platform.robot.config.JsonbStringTypeHandler.class)
    private String inputSchema;

    @TableField(typeHandler = io.opengeobot.platform.robot.config.JsonbStringTypeHandler.class)
    private String outputSchema;

    private OffsetDateTime publishedAt;

    private String publishedBy;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
