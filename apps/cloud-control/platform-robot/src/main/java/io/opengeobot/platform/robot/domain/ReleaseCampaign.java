/*
 * Function: ReleaseCampaign entity — maps to ota.release_campaign table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.opengeobot.platform.robot.config.StringArrayTypeHandler;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Persistent release campaign entity backed by the
 * {@code ota.release_campaign} table. Campaigns follow the SM-OTA-001 state
 * machine (CREATED → IN_PROGRESS → COMPLETED / ROLLED_BACK / FAILED). The
 * {@code targetRobotIds} array holds the robots selected for deployment;
 * {@code canaryPercent} controls the canary wave size.
 */
@TableName(value = "release_campaign", schema = "ota", autoResultMap = true)
public class ReleaseCampaign {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String campaignId;

    private String packageId;

    private Integer canaryPercent;

    private String status;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private List<String> targetRobotIds;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    private OffsetDateTime createdAt;

    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public Integer getCanaryPercent() {
        return canaryPercent;
    }

    public void setCanaryPercent(Integer canaryPercent) {
        this.canaryPercent = canaryPercent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getTargetRobotIds() {
        return targetRobotIds;
    }

    public void setTargetRobotIds(List<String> targetRobotIds) {
        this.targetRobotIds = targetRobotIds;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
