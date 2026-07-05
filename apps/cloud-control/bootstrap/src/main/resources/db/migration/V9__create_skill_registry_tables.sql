-- Function: Create skill registry tables for F-SKILL-001 skill lifecycle
-- Time: 2026-07-05
-- Author: AxeXie

-- Skill table (versioned, follows SM-SKILL-001: DRAFT → PUBLISHED → DEPRECATED/DISABLED)
CREATE TABLE skill_registry.skill (
    id BIGSERIAL PRIMARY KEY,
    skill_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    module VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version INTEGER NOT NULL DEFAULT 0,
    input_schema JSONB,
    output_schema JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

ALTER TABLE skill_registry.skill ADD CONSTRAINT uq_skill_skill_id UNIQUE (skill_id);
ALTER TABLE skill_registry.skill ADD CONSTRAINT uq_skill_name UNIQUE (name);
CREATE INDEX idx_skill_status ON skill_registry.skill (status);
CREATE INDEX idx_skill_module ON skill_registry.skill (module);

-- Skill version table (append-only, immutable version snapshots)
CREATE TABLE skill_registry.skill_version (
    id BIGSERIAL PRIMARY KEY,
    skill_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    changelog TEXT,
    input_schema JSONB,
    output_schema JSONB,
    published_at TIMESTAMPTZ,
    published_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE skill_registry.skill_version ADD CONSTRAINT uq_skill_version UNIQUE (skill_id, version);
CREATE INDEX idx_skill_version_skill_id ON skill_registry.skill_version (skill_id);

-- Seed: basic platform skills (PUBLISHED with version 1)
INSERT INTO skill_registry.skill (skill_id, name, module, description, status, current_version, input_schema, output_schema, created_at, updated_at, created_by, updated_by)
VALUES
('skl_01J00000000000000000000001', 'stand_up', 'locomotion', 'Commands the robot to stand up from a seated or prone position.', 'PUBLISHED', 1,
 '{"type":"object","properties":{"speed":{"type":"number","default":0.5,"minimum":0,"maximum":1}}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"}}}'::jsonb,
 NOW(), NOW(), 'system', 'system'),
('skl_01J00000000000000000000002', 'stop', 'locomotion', 'Commands the robot to stop all motion immediately.', 'PUBLISHED', 1,
 '{"type":"object","properties":{}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"}}}'::jsonb,
 NOW(), NOW(), 'system', 'system'),
('skl_01J00000000000000000000003', 'move_forward', 'locomotion', 'Commands the robot to move forward at a given speed for a given duration.', 'PUBLISHED', 1,
 '{"type":"object","properties":{"speed":{"type":"number","default":0.3,"minimum":0,"maximum":1},"duration":{"type":"number","default":1.0,"minimum":0}}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"}}}'::jsonb,
 NOW(), NOW(), 'system', 'system'),
('skl_01J00000000000000000000004', 'capture_image', 'perception', 'Captures an image from the robot camera and stores it as a media asset.', 'PUBLISHED', 1,
 '{"type":"object","properties":{"camera_id":{"type":"string"},"resolution":{"type":"string","default":"1080p"}}}'::jsonb,
 '{"type":"object","properties":{"asset_id":{"type":"string"}}}'::jsonb,
 NOW(), NOW(), 'system', 'system'),
('skl_01J00000000000000000000005', 'emergency_stop', 'safety', 'Triggers an immediate emergency stop of all robot motion. Must be registered and versioned per the platform safety red lines.', 'PUBLISHED', 1,
 '{"type":"object","properties":{"reason":{"type":"string"}}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"},"estop_triggered":{"type":"boolean"}}}'::jsonb,
 NOW(), NOW(), 'system', 'system');

-- Seed: skill version rows for the published skills
INSERT INTO skill_registry.skill_version (skill_id, version, status, changelog, input_schema, output_schema, published_at, published_by, created_at)
VALUES
('skl_01J00000000000000000000001', 1, 'PUBLISHED', 'Initial published version of stand_up skill.',
 '{"type":"object","properties":{"speed":{"type":"number","default":0.5,"minimum":0,"maximum":1}}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"}}}'::jsonb,
 NOW(), 'system', NOW()),
('skl_01J00000000000000000000002', 1, 'PUBLISHED', 'Initial published version of stop skill.',
 '{"type":"object","properties":{}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"}}}'::jsonb,
 NOW(), 'system', NOW()),
('skl_01J00000000000000000000003', 1, 'PUBLISHED', 'Initial published version of move_forward skill.',
 '{"type":"object","properties":{"speed":{"type":"number","default":0.3,"minimum":0,"maximum":1},"duration":{"type":"number","default":1.0,"minimum":0}}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"}}}'::jsonb,
 NOW(), 'system', NOW()),
('skl_01J00000000000000000000004', 1, 'PUBLISHED', 'Initial published version of capture_image skill.',
 '{"type":"object","properties":{"camera_id":{"type":"string"},"resolution":{"type":"string","default":"1080p"}}}'::jsonb,
 '{"type":"object","properties":{"asset_id":{"type":"string"}}}'::jsonb,
 NOW(), 'system', NOW()),
('skl_01J00000000000000000000005', 1, 'PUBLISHED', 'Initial published version of emergency_stop skill.',
 '{"type":"object","properties":{"reason":{"type":"string"}}}'::jsonb,
 '{"type":"object","properties":{"success":{"type":"boolean"},"estop_triggered":{"type":"boolean"}}}'::jsonb,
 NOW(), 'system', NOW());
