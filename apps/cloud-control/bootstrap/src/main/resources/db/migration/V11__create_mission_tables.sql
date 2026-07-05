-- Function: Create mission tables for F-MISSION-001/002/003
-- Time: 2026-07-05
-- Author: AxeXie

-- Mission table (SM-MISSION-001 state machine)
CREATE TABLE mission.mission (
    id BIGSERIAL PRIMARY KEY,
    mission_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    robot_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    scheduled_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_reason TEXT,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(64),
    trace_id VARCHAR(64)
);

ALTER TABLE mission.mission ADD CONSTRAINT uq_mission_id UNIQUE (mission_id);
CREATE INDEX idx_mission_status ON mission.mission (status);
CREATE INDEX idx_mission_robot ON mission.mission (robot_id);
CREATE INDEX idx_mission_created ON mission.mission (created_at);

-- Mission step table (SM-MISSION-002 state machine)
CREATE TABLE mission.mission_step (
    id BIGSERIAL PRIMARY KEY,
    step_id VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    step_order INTEGER NOT NULL,
    input_params JSONB,
    output_result JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT
);

ALTER TABLE mission.mission_step ADD CONSTRAINT uq_mission_step_id UNIQUE (step_id);
CREATE INDEX idx_mission_step_mission ON mission.mission_step (mission_id);
CREATE INDEX idx_mission_step_order ON mission.mission_step (mission_id, step_order);

-- Mission template table (versioned blueprint for fast mission creation)
CREATE TABLE mission.mission_template (
    id BIGSERIAL PRIMARY KEY,
    template_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    steps JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64)
);

ALTER TABLE mission.mission_template ADD CONSTRAINT uq_mission_template_id UNIQUE (template_id);
CREATE INDEX idx_mission_template_name ON mission.mission_template (name);

-- Mission approval table (SM-APPROVAL-001 state machine)
CREATE TABLE mission.mission_approval (
    id BIGSERIAL PRIMARY KEY,
    mission_id VARCHAR(64) NOT NULL,
    approver_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    comment TEXT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mission_approval_mission ON mission.mission_approval (mission_id);
CREATE INDEX idx_mission_approval_status ON mission.mission_approval (status);
