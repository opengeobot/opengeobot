-- Function: Create safety tables for F-SAFETY-001 emergency stop and safety state
-- Time: 2026-07-05
-- Author: AxeXie

-- Safety state table (SM-SAFETY-001 state machine: NORMAL → EMERGENCY_STOPPED → RESETTING → NORMAL)
CREATE TABLE policy.safety_state (
    id BIGSERIAL PRIMARY KEY,
    robot_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    last_event_at TIMESTAMPTZ,
    reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE policy.safety_state ADD CONSTRAINT uq_safety_state_robot UNIQUE (robot_id);
CREATE INDEX idx_safety_state_state ON policy.safety_state (state);

-- Safety event table (append-only event log)
CREATE TABLE policy.safety_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    robot_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64),
    actor_id VARCHAR(64)
);

ALTER TABLE policy.safety_event ADD CONSTRAINT uq_safety_event_id UNIQUE (event_id);
CREATE INDEX idx_safety_event_robot ON policy.safety_event (robot_id);
CREATE INDEX idx_safety_event_type ON policy.safety_event (event_type);
CREATE INDEX idx_safety_event_occurred ON policy.safety_event (occurred_at);
