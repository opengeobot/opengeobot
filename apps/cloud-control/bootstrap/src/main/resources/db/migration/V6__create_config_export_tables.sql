-- Function: Create config and export tables for F-PLATFORM-004
-- Time: 2026-07-04
-- Author: AxeXie

-- Config table (versioned)
CREATE TABLE platform_governance.sys_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(256) NOT NULL,
    config_value TEXT NOT NULL,
    value_type VARCHAR(32) NOT NULL DEFAULT 'string',
    module VARCHAR(64) NOT NULL DEFAULT 'platform',
    description VARCHAR(512),
    encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

ALTER TABLE platform_governance.sys_config ADD CONSTRAINT uq_config_key UNIQUE (config_key);
CREATE INDEX idx_config_module ON platform_governance.sys_config (module);
CREATE INDEX idx_config_status ON platform_governance.sys_config (status);

-- Config history table (append-only, for version tracking)
CREATE TABLE platform_governance.sys_config_history (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(256) NOT NULL,
    config_value TEXT NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    module VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    changed_by VARCHAR(64),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64),
    change_type VARCHAR(32) NOT NULL
);

CREATE INDEX idx_config_history_key ON platform_governance.sys_config_history (config_key);
CREATE INDEX idx_config_history_time ON platform_governance.sys_config_history (changed_at);

-- Export operation table
CREATE TABLE platform_governance.export_operation (
    id BIGSERIAL PRIMARY KEY,
    export_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    format VARCHAR(32) NOT NULL DEFAULT 'csv',
    filter JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    file_url VARCHAR(512),
    file_size BIGINT,
    file_path VARCHAR(512),
    error_message TEXT,
    requested_by VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64)
);

ALTER TABLE platform_governance.export_operation ADD CONSTRAINT uq_export_id UNIQUE (export_id);
CREATE INDEX idx_export_status ON platform_governance.export_operation (status);
CREATE INDEX idx_export_requested_by ON platform_governance.export_operation (requested_by);
CREATE INDEX idx_export_created ON platform_governance.export_operation (created_at);

-- Seed: basic platform configs
INSERT INTO platform_governance.sys_config (config_key, config_value, value_type, module, description, version)
VALUES
('platform.name', 'OpenGeoBot 一脑多控平台', 'string', 'platform', 'Platform display name', 1),
('platform.version', '0.1.0', 'string', 'platform', 'Platform version', 1),
('platform.session.timeout_minutes', '120', 'number', 'iam', 'Session timeout in minutes', 1),
('platform.token.access_expiry_seconds', '1800', 'number', 'iam', 'Access token expiry in seconds', 1),
('platform.token.refresh_expiry_days', '7', 'number', 'iam', 'Refresh token expiry in days', 1),
('platform.password.min_length', '8', 'number', 'iam', 'Minimum password length', 1),
('platform.password.require_uppercase', 'true', 'boolean', 'iam', 'Require uppercase in password', 1),
('platform.password.require_digit', 'true', 'boolean', 'iam', 'Require digit in password', 1),
('platform.audit.retention_days', '365', 'number', 'governance', 'Audit log retention in days', 1),
('platform.export.max_rows', '100000', 'number', 'governance', 'Maximum rows per export', 1);
