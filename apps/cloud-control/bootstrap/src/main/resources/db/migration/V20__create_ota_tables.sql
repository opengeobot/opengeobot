-- Function: Create OTA tables for F-OTA-001 firmware/skill package publishing
-- Time: 2026-07-05
-- Author: AxeXie

-- Dedicated schema for OTA artifacts (manifest owner_schema is ops; this schema
-- isolates OTA domain tables per the implementation blueprint).
CREATE SCHEMA IF NOT EXISTS ota;

-- Firmware/skill package table (artifact registry)
CREATE TABLE ota.firmware_package (
    id BIGSERIAL PRIMARY KEY,
    package_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    version VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64)
);

ALTER TABLE ota.firmware_package ADD CONSTRAINT uq_ota_package_id UNIQUE (package_id);
CREATE INDEX idx_ota_package_type ON ota.firmware_package (type);
CREATE INDEX idx_ota_package_name ON ota.firmware_package (name);

-- Release campaign table (SM-OTA-001: CREATED → IN_PROGRESS → COMPLETED / ROLLED_BACK / FAILED)
CREATE TABLE ota.release_campaign (
    id BIGSERIAL PRIMARY KEY,
    campaign_id VARCHAR(64) NOT NULL,
    package_id VARCHAR(64) NOT NULL,
    canary_percent INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    target_robot_ids text[],
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64)
);

ALTER TABLE ota.release_campaign ADD CONSTRAINT uq_ota_campaign_id UNIQUE (campaign_id);
CREATE INDEX idx_ota_campaign_status ON ota.release_campaign (status);
CREATE INDEX idx_ota_campaign_package ON ota.release_campaign (package_id);

-- Deployment record table (SM-OTA-002: PENDING → IN_PROGRESS → SUCCESS / FAILED / ROLLED_BACK)
CREATE TABLE ota.deployment_record (
    id BIGSERIAL PRIMARY KEY,
    record_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(64) NOT NULL,
    robot_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT
);

ALTER TABLE ota.deployment_record ADD CONSTRAINT uq_ota_deployment_id UNIQUE (record_id);
CREATE INDEX idx_ota_deployment_campaign ON ota.deployment_record (campaign_id);
CREATE INDEX idx_ota_deployment_robot ON ota.deployment_record (robot_id);
CREATE INDEX idx_ota_deployment_status ON ota.deployment_record (status);
