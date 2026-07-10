/*
 * Function: Control lease entity — maps to fleet.control_lease for F-MONITOR-001
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent control lease entity backed by {@code fleet.control_lease}.
 * ACTIVE leases carry a unique fencing token for edge Safety Gateway checks.
 */
@TableName(value = "control_lease", schema = "fleet")
public class ControlLease {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String leaseId;

    private String robotId;

    private String holderUserId;

    private String status;

    private OffsetDateTime acquiredAt;

    private OffsetDateTime expiresAt;

    private OffsetDateTime releasedAt;

    private String fencingToken;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(String leaseId) {
        this.leaseId = leaseId;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getHolderUserId() {
        return holderUserId;
    }

    public void setHolderUserId(String holderUserId) {
        this.holderUserId = holderUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(OffsetDateTime acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(OffsetDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getFencingToken() {
        return fencingToken;
    }

    public void setFencingToken(String fencingToken) {
        this.fencingToken = fencingToken;
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
}
