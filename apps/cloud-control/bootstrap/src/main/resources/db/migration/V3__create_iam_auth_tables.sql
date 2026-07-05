-- Function: Create authentication tables for F-PLATFORM-001
-- Time: 2026-07-04
-- Author: AxeXie

-- User table
CREATE TABLE platform_iam.sys_user (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(256),
    email VARCHAR(256),
    phone VARCHAR(32),
    avatar VARCHAR(512),
    password_hash VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    last_login_ip VARCHAR(45),
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

-- Unique constraints
ALTER TABLE platform_iam.sys_user ADD CONSTRAINT uq_user_user_id UNIQUE (user_id);
ALTER TABLE platform_iam.sys_user ADD CONSTRAINT uq_user_username UNIQUE (username);
ALTER TABLE platform_iam.sys_user ADD CONSTRAINT uq_user_email UNIQUE (email);

-- Indexes
CREATE INDEX idx_user_status ON platform_iam.sys_user (status);

-- Refresh token table
CREATE TABLE platform_iam.sys_refresh_token (
    id BIGSERIAL PRIMARY KEY,
    token_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    token_hash VARCHAR(256) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE platform_iam.sys_refresh_token ADD CONSTRAINT uq_refresh_token_id UNIQUE (token_id);
CREATE INDEX idx_refresh_user ON platform_iam.sys_refresh_token (user_id);
CREATE INDEX idx_refresh_expires ON platform_iam.sys_refresh_token (expires_at);

-- Session table
CREATE TABLE platform_iam.sys_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    source_ip VARCHAR(45),
    user_agent TEXT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_refreshed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(128),
    trace_id VARCHAR(64)
);

ALTER TABLE platform_iam.sys_session ADD CONSTRAINT uq_session_id UNIQUE (session_id);
CREATE INDEX idx_session_user ON platform_iam.sys_session (user_id);
CREATE INDEX idx_session_state ON platform_iam.sys_session (state);
CREATE INDEX idx_session_expires ON platform_iam.sys_session (expires_at);

-- Seed default admin user (password: admin123, BCrypt hash)
INSERT INTO platform_iam.sys_user (user_id, username, display_name, status, password_hash)
VALUES ('usr_01J00000000000000000000001', 'admin', 'System Administrator', 'ACTIVE',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
