-- Function: Create robot registry tables for F-ROBOT-001
-- Time: 2026-07-05
-- Author: AxeXie

-- Robot table (registered robot identity and observed status)
CREATE TABLE robot_registry.robot (
    id BIGSERIAL PRIMARY KEY,
    robot_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    model_id VARCHAR(64) NOT NULL,
    serial_number VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
    org_id VARCHAR(64) NOT NULL,
    last_seen_at TIMESTAMPTZ,
    last_seen_ip VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

ALTER TABLE robot_registry.robot ADD CONSTRAINT uq_robot_robot_id UNIQUE (robot_id);
ALTER TABLE robot_registry.robot ADD CONSTRAINT uq_robot_serial_number UNIQUE (serial_number);
CREATE INDEX idx_robot_status ON robot_registry.robot (status);
CREATE INDEX idx_robot_org ON robot_registry.robot (org_id);
CREATE INDEX idx_robot_model ON robot_registry.robot (model_id);

-- Robot capability table (declared capabilities per robot)
CREATE TABLE robot_registry.robot_capability (
    id BIGSERIAL PRIMARY KEY,
    robot_id VARCHAR(64) NOT NULL,
    capability_type VARCHAR(128) NOT NULL,
    capability_value VARCHAR(128) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE robot_registry.robot_capability ADD CONSTRAINT uq_robot_capability UNIQUE (robot_id, capability_type);
CREATE INDEX idx_robot_capability_robot ON robot_registry.robot_capability (robot_id);
CREATE INDEX idx_robot_capability_type ON robot_registry.robot_capability (capability_type);

-- Robot status history table (audit-grade transition log for SM-ROBOT-001)
CREATE TABLE robot_registry.robot_status_history (
    id BIGSERIAL PRIMARY KEY,
    robot_id VARCHAR(64) NOT NULL,
    old_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    reason VARCHAR(256),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64)
);

CREATE INDEX idx_robot_status_history_robot ON robot_registry.robot_status_history (robot_id);
CREATE INDEX idx_robot_status_history_occurred ON robot_registry.robot_status_history (occurred_at);

-- Seed: example robot
INSERT INTO robot_registry.robot (robot_id, name, model_id, serial_number, status, org_id, created_by, updated_by)
VALUES ('rbt_01J00000000000000000000001', 'Pioneer-01', 'mdl_01J00000000000000000000001', 'SN-PIONEER-0001', 'OFFLINE', 'org_01J00000000000000000000001', 'system', 'system');

INSERT INTO robot_registry.robot_capability (robot_id, capability_type, capability_value, details)
VALUES
('rbt_01J00000000000000000000001', 'navigation', 'enabled', '{"max_speed": 1.5, "localization": "amcl"}'::jsonb),
('rbt_01J00000000000000000000001', 'perception', 'enabled', '{"sensors": ["lidar", "depth_camera"]}'::jsonb);
