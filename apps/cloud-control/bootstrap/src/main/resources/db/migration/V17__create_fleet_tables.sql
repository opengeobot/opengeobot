-- Function: Create fleet scheduling tables for F-FLEET-001
-- Time: 2026-07-05
-- Author: AxeXie

-- Fleet schedule table (assigns missions to robots within planned time windows)
CREATE TABLE fleet.fleet_schedule (
    id BIGSERIAL PRIMARY KEY,
    schedule_id VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    robot_id VARCHAR(64) NOT NULL,
    planned_start TIMESTAMPTZ NOT NULL,
    planned_end TIMESTAMPTZ NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64)
);

ALTER TABLE fleet.fleet_schedule ADD CONSTRAINT uq_fleet_schedule_id UNIQUE (schedule_id);
CREATE INDEX idx_fleet_schedule_status ON fleet.fleet_schedule (status);
CREATE INDEX idx_fleet_schedule_robot ON fleet.fleet_schedule (robot_id);
CREATE INDEX idx_fleet_schedule_mission ON fleet.fleet_schedule (mission_id);
CREATE INDEX idx_fleet_schedule_planned ON fleet.fleet_schedule (planned_start, planned_end);

-- Conflict record table (detected conflicts between schedules)
-- schedule_ids stored as JSONB array for MyBatis-Plus compatibility (no native TEXT[] handler)
CREATE TABLE fleet.conflict_record (
    id BIGSERIAL PRIMARY KEY,
    conflict_id VARCHAR(64) NOT NULL,
    schedule_ids JSONB NOT NULL DEFAULT '[]',
    conflict_type VARCHAR(32) NOT NULL,
    description TEXT,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolution VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    trace_id VARCHAR(64)
);

ALTER TABLE fleet.conflict_record ADD CONSTRAINT uq_fleet_conflict_id UNIQUE (conflict_id);
CREATE INDEX idx_fleet_conflict_status ON fleet.conflict_record (status);
CREATE INDEX idx_fleet_conflict_detected ON fleet.conflict_record (detected_at);

-- Failover event table (records mission transfers between robots)
CREATE TABLE fleet.failover_event (
    id BIGSERIAL PRIMARY KEY,
    failover_id VARCHAR(64) NOT NULL,
    robot_id VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    target_robot_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'INITIATED',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64)
);

ALTER TABLE fleet.failover_event ADD CONSTRAINT uq_fleet_failover_id UNIQUE (failover_id);
CREATE INDEX idx_fleet_failover_robot ON fleet.failover_event (robot_id);
CREATE INDEX idx_fleet_failover_mission ON fleet.failover_event (mission_id);
CREATE INDEX idx_fleet_failover_status ON fleet.failover_event (status);
CREATE INDEX idx_fleet_failover_occurred ON fleet.failover_event (occurred_at);
