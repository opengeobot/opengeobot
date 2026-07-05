-- Function: Create map/scene tables for F-MAP-001 map and area management
-- Time: 2026-07-05
-- Author: AxeXie

-- Map table (SM-MAP-001 state machine: DRAFT → PUBLISHED → ARCHIVED)
CREATE TABLE map_scene.map (
    id BIGSERIAL PRIMARY KEY,
    map_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    metadata JSONB,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(64)
);

ALTER TABLE map_scene.map ADD CONSTRAINT uq_map_id UNIQUE (map_id);
CREATE INDEX idx_map_status ON map_scene.map (status);
CREATE INDEX idx_map_name ON map_scene.map (name);

-- Scene table (optional scene grouping for maps)
CREATE TABLE map_scene.scene (
    id BIGSERIAL PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    map_ids JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE map_scene.scene ADD CONSTRAINT uq_scene_id UNIQUE (scene_id);

-- Area table (named areas within a map: zones, waypoints, paths, docks)
CREATE TABLE map_scene.area (
    id BIGSERIAL PRIMARY KEY,
    area_id VARCHAR(64) NOT NULL,
    map_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    type VARCHAR(32) NOT NULL,
    geometry JSONB NOT NULL,
    properties JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE map_scene.area ADD CONSTRAINT uq_area_id UNIQUE (area_id);
CREATE INDEX idx_area_map ON map_scene.area (map_id);
CREATE INDEX idx_area_type ON map_scene.area (type);

-- Restricted area table (no-entry, speed limit, time window restrictions)
CREATE TABLE map_scene.restricted_area (
    id BIGSERIAL PRIMARY KEY,
    area_id VARCHAR(64) NOT NULL,
    map_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    restriction_type VARCHAR(32) NOT NULL,
    geometry JSONB NOT NULL,
    properties JSONB,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE map_scene.restricted_area ADD CONSTRAINT uq_restricted_area_id UNIQUE (area_id);
CREATE INDEX idx_restricted_area_map ON map_scene.restricted_area (map_id);
CREATE INDEX idx_restricted_area_type ON map_scene.restricted_area (restriction_type);
