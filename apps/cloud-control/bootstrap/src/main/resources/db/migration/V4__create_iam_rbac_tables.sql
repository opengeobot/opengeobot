-- Function: Create RBAC tables for F-PLATFORM-002
-- Time: 2026-07-04
-- Author: AxeXie

-- Organization table (tree structure)
CREATE TABLE platform_iam.sys_org (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64),
    org_name VARCHAR(256) NOT NULL,
    org_code VARCHAR(128) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    path VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

ALTER TABLE platform_iam.sys_org ADD CONSTRAINT uq_org_id UNIQUE (org_id);
ALTER TABLE platform_iam.sys_org ADD CONSTRAINT uq_org_code UNIQUE (org_code);
CREATE INDEX idx_org_parent ON platform_iam.sys_org (parent_id);
CREATE INDEX idx_org_path ON platform_iam.sys_org (path);

-- User-Organization association table
CREATE TABLE platform_iam.sys_user_org (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE platform_iam.sys_user_org ADD CONSTRAINT uq_user_org UNIQUE (user_id, org_id);
CREATE INDEX idx_user_org_user ON platform_iam.sys_user_org (user_id);
CREATE INDEX idx_user_org_org ON platform_iam.sys_user_org (org_id);

-- Add org_id to sys_user (primary org, quick access)
-- Note: sys_user already created in V3, we add column here
ALTER TABLE platform_iam.sys_user ADD COLUMN IF NOT EXISTS primary_org_id VARCHAR(64);

-- Role table
CREATE TABLE platform_iam.sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    role_code VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

ALTER TABLE platform_iam.sys_role ADD CONSTRAINT uq_role_id UNIQUE (role_id);
ALTER TABLE platform_iam.sys_role ADD CONSTRAINT uq_role_code UNIQUE (role_code);

-- Permission table (stable permission codes)
CREATE TABLE platform_iam.sys_permission (
    id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(256) NOT NULL,
    module VARCHAR(64) NOT NULL,
    description TEXT,
    resource_type VARCHAR(64),
    action VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE platform_iam.sys_permission ADD CONSTRAINT uq_permission_code UNIQUE (permission_code);

-- User-Role association
CREATE TABLE platform_iam.sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by VARCHAR(64),
    expires_at TIMESTAMPTZ
);

ALTER TABLE platform_iam.sys_user_role ADD CONSTRAINT uq_user_role UNIQUE (user_id, role_id);
CREATE INDEX idx_user_role_user ON platform_iam.sys_user_role (user_id);
CREATE INDEX idx_user_role_role ON platform_iam.sys_user_role (role_id);

-- Role-Permission association
CREATE TABLE platform_iam.sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64)
);

ALTER TABLE platform_iam.sys_role_permission ADD CONSTRAINT uq_role_perm UNIQUE (role_id, permission_code);
CREATE INDEX idx_role_perm_role ON platform_iam.sys_role_permission (role_id);
CREATE INDEX idx_role_perm_code ON platform_iam.sys_role_permission (permission_code);

-- Seed: default organization
INSERT INTO platform_iam.sys_org (org_id, parent_id, org_name, org_code, description, sort_order, path)
VALUES ('org_01J00000000000000000000001', NULL, 'OpenGeoBot', 'OPENGEOBOT', 'Root organization', 0, '/org_01J00000000000000000000001');

-- Link admin user to root org
INSERT INTO platform_iam.sys_user_org (user_id, org_id, is_primary)
SELECT 'usr_01J00000000000000000000001', 'org_01J00000000000000000000001', TRUE
WHERE NOT EXISTS (SELECT 1 FROM platform_iam.sys_user_org WHERE user_id = 'usr_01J00000000000000000000001');

-- Update admin user's primary org
UPDATE platform_iam.sys_user SET primary_org_id = 'org_01J00000000000000000000001' WHERE user_id = 'usr_01J00000000000000000000001' AND primary_org_id IS NULL;

-- Seed: roles
INSERT INTO platform_iam.sys_role (role_id, role_name, role_code, description, sort_order, built_in)
VALUES
('rol_01J00000000000000000000001', 'System Administrator', 'SYS_ADMIN', 'Full system access', 0, TRUE),
('rol_01J00000000000000000000002', 'Operator', 'OPERATOR', 'Robot operation access', 1, TRUE),
('rol_01J00000000000000000000003', 'Viewer', 'VIEWER', 'Read-only access', 2, TRUE);

-- Seed: permissions
INSERT INTO platform_iam.sys_permission (permission_code, permission_name, module, description, resource_type, action)
VALUES
('platform.profile.read', 'View Profile', 'iam', 'View own profile', 'profile', 'read'),
('platform.profile.manage', 'Manage Profile', 'iam', 'Manage own profile', 'profile', 'manage'),
('platform.user.read', 'View Users', 'iam', 'View user list', 'user', 'read'),
('platform.user.manage', 'Manage Users', 'iam', 'Create/edit/disable users', 'user', 'manage'),
('platform.org.manage', 'Manage Organizations', 'iam', 'Create/edit organizations', 'org', 'manage'),
('platform.role.read', 'View Roles', 'iam', 'View role list', 'role', 'read'),
('platform.role.manage', 'Manage Roles', 'iam', 'Create/edit roles and assign permissions', 'role', 'manage'),
('platform.permission.read', 'View Permissions', 'iam', 'View permission codes', 'permission', 'read'),
('platform.dictionary.read', 'View Dictionary', 'governance', 'View dictionary types and items', 'dictionary', 'read'),
('platform.dictionary.manage', 'Manage Dictionary', 'governance', 'Create/edit/publish dictionary', 'dictionary', 'manage'),
('platform.i18n.read', 'View I18n', 'governance', 'View i18n resources', 'i18n', 'read'),
('platform.i18n.manage', 'Manage I18n', 'governance', 'Create/edit i18n resources', 'i18n', 'manage'),
('platform.config.read', 'View Config', 'governance', 'View platform config', 'config', 'read'),
('platform.config.manage', 'Manage Config', 'governance', 'Create/edit config', 'config', 'manage'),
('audit.audit.read', 'View Audit', 'governance', 'View audit logs', 'audit', 'read'),
('audit.audit.export', 'Export Audit', 'governance', 'Export audit logs', 'audit', 'export');

-- Assign all permissions to SYS_ADMIN role
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000001', permission_code FROM platform_iam.sys_permission;

-- Assign read permissions to Viewer role
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000003', permission_code FROM platform_iam.sys_permission WHERE permission_code LIKE '%.read';

-- Assign SYS_ADMIN role to admin user
INSERT INTO platform_iam.sys_user_role (user_id, role_id, assigned_by)
VALUES ('usr_01J00000000000000000000001', 'rol_01J00000000000000000000001', 'system');
