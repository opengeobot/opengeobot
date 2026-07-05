-- Function: Create backup and recovery tables for F-RECOVERY-001
-- Time: 2026-07-05
-- Author: AxeXie

-- Dedicated schema for recovery domain tables.
CREATE SCHEMA IF NOT EXISTS recovery;

-- Backup record table (SM-BACKUP-OPERATION: RUNNING → COMPLETED / FAILED)
CREATE TABLE recovery.backup_record (
    id BIGSERIAL PRIMARY KEY,
    backup_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    created_by VARCHAR(64)
);

ALTER TABLE recovery.backup_record ADD CONSTRAINT uq_recovery_backup_id UNIQUE (backup_id);
CREATE INDEX idx_recovery_backup_type ON recovery.backup_record (type);
CREATE INDEX idx_recovery_backup_status ON recovery.backup_record (status);
CREATE INDEX idx_recovery_backup_started ON recovery.backup_record (started_at);

-- Restore record table (SM-RESTORE-OPERATION: RUNNING → COMPLETED / FAILED)
CREATE TABLE recovery.restore_record (
    id BIGSERIAL PRIMARY KEY,
    restore_id VARCHAR(64) NOT NULL,
    backup_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    restored_by VARCHAR(64)
);

ALTER TABLE recovery.restore_record ADD CONSTRAINT uq_recovery_restore_id UNIQUE (restore_id);
CREATE INDEX idx_recovery_restore_backup ON recovery.restore_record (backup_id);
CREATE INDEX idx_recovery_restore_status ON recovery.restore_record (status);

-- Drill record table (disaster recovery drills)
CREATE TABLE recovery.drill_record (
    id BIGSERIAL PRIMARY KEY,
    drill_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    result VARCHAR(32) NOT NULL,
    notes TEXT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    executed_by VARCHAR(64)
);

ALTER TABLE recovery.drill_record ADD CONSTRAINT uq_recovery_drill_id UNIQUE (drill_id);
CREATE INDEX idx_recovery_drill_type ON recovery.drill_record (type);
CREATE INDEX idx_recovery_drill_result ON recovery.drill_record (result);
CREATE INDEX idx_recovery_drill_executed ON recovery.drill_record (executed_at);
