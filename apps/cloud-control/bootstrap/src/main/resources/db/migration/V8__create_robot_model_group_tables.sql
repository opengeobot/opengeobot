-- Function: Create robot model and group tables for F-ROBOT-002
-- Time: 2026-07-05
-- Author: AxeXie

-- Robot model table (catalogue of supported robot hardware models)
CREATE TABLE robot_registry.robot_model (
    id BIGSERIAL PRIMARY KEY,
    model_id VARCHAR(64) NOT NULL,
    model_name VARCHAR(256) NOT NULL,
    manufacturer VARCHAR(256),
    description TEXT,
    capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE robot_registry.robot_model ADD CONSTRAINT uq_robot_model_model_id UNIQUE (model_id);
CREATE INDEX idx_robot_model_name ON robot_registry.robot_model (model_name);
CREATE INDEX idx_robot_model_manufacturer ON robot_registry.robot_model (manufacturer);

-- Robot group table (hierarchical grouping of robots)
CREATE TABLE robot_registry.robot_group (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64),
    group_name VARCHAR(256) NOT NULL,
    description TEXT,
    path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE robot_registry.robot_group ADD CONSTRAINT uq_robot_group_group_id UNIQUE (group_id);
CREATE INDEX idx_robot_group_parent ON robot_registry.robot_group (parent_id);
CREATE INDEX idx_robot_group_path ON robot_registry.robot_group (path);

-- Robot group member table (associates robots with groups)
CREATE TABLE robot_registry.robot_group_member (
    id BIGSERIAL PRIMARY KEY,
    robot_id VARCHAR(64) NOT NULL,
    group_id VARCHAR(64) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE robot_registry.robot_group_member ADD CONSTRAINT uq_robot_group_member UNIQUE (robot_id, group_id);
CREATE INDEX idx_robot_group_member_robot ON robot_registry.robot_group_member (robot_id);
CREATE INDEX idx_robot_group_member_group ON robot_registry.robot_group_member (group_id);

-- Seed: example robot model
INSERT INTO robot_registry.robot_model (model_id, model_name, manufacturer, description, capabilities)
VALUES (
    'mdl_01J00000000000000000000001',
    'Unitree Go2',
    'Unitree Robotics',
    'Quadruped robot dog with embedded AI and ROS2 support',
    '[{"capability_type": "navigation", "capability_value": "enabled"}, {"capability_type": "perception", "capability_value": "enabled"}]'::jsonb
);

-- Seed: example robot group
INSERT INTO robot_registry.robot_group (group_id, parent_id, group_name, description, path)
VALUES (
    'grp_01J00000000000000000000001',
    NULL,
    'Default Fleet',
    'Default robot fleet containing all registered robots',
    '/grp_01J00000000000000000000001'
);
