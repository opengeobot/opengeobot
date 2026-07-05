/*
 * Function: RestoreRecord entity — maps to recovery.restore_record table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent restore record backed by the {@code recovery.restore_record}
 * table. Restores follow SM-RESTORE-OPERATION (RUNNING → COMPLETED / FAILED).
 */
@TableName(value = "restore_record", schema = "recovery", autoResultMap = true)
public class RestoreRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String restoreId;

    private String backupId;

    private String status;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    private String errorMessage;

    private String restoredBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRestoreId() {
        return restoreId;
    }

    public void setRestoreId(String restoreId) {
        this.restoreId = restoreId;
    }

    public String getBackupId() {
        return backupId;
    }

    public void setBackupId(String backupId) {
        this.backupId = backupId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRestoredBy() {
        return restoredBy;
    }

    public void setRestoredBy(String restoredBy) {
        this.restoredBy = restoredBy;
    }
}
