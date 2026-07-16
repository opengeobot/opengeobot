-- Function: Add missing mission permission codes for F-MISSION-001/002/003
-- Time: 2026-07-16
-- Author: AxeXie
--
-- The MissionController uses @PreAuthorize with permission codes that were
-- never seeded into sys_permission: mission.mission.create, .pause, .cancel
-- and .approve. Only mission.mission.read and mission.mission.manage existed.
-- This migration adds the missing codes and grants them to SYS_ADMIN and
-- OPERATOR roles idempotently.

-- Seed missing mission permission codes (idempotent)
INSERT INTO platform_iam.sys_permission (permission_code, permission_name, module, description, resource_type, action)
SELECT v.permission_code, v.permission_name, v.module, v.description, v.resource_type, v.action
FROM (VALUES
    ('mission.mission.read',   'View Missions',       'mission', 'View mission list and detail',                'mission', 'read'),
    ('mission.mission.manage', 'Manage Missions',      'mission', 'Create, edit and delete missions',            'mission', 'manage'),
    ('mission.mission.create', 'Create/Update Mission','mission', 'Create, update, plan and start missions',      'mission', 'create'),
    ('mission.mission.pause',  'Pause/Resume Mission', 'mission', 'Pause and resume mission execution',         'mission', 'pause'),
    ('mission.mission.cancel', 'Cancel Mission',       'mission', 'Cancel a mission',                            'mission', 'cancel'),
    ('mission.mission.approve', 'Approve/Reject Mission','mission','Approve or reject a mission approval request','mission','approve')
) AS v(permission_code, permission_name, module, description, resource_type, action)
WHERE NOT EXISTS (
    SELECT 1 FROM platform_iam.sys_permission p WHERE p.permission_code = v.permission_code
);

-- Grant all mission permissions to SYS_ADMIN (idempotent)
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000001', v.permission_code
FROM (VALUES
    ('mission.mission.read'),
    ('mission.mission.manage'),
    ('mission.mission.create'),
    ('mission.mission.pause'),
    ('mission.mission.cancel'),
    ('mission.mission.approve')
) AS v(permission_code)
WHERE NOT EXISTS (
    SELECT 1 FROM platform_iam.sys_role_permission rp
    WHERE rp.role_id = 'rol_01J00000000000000000000001'
      AND rp.permission_code = v.permission_code
);

-- Grant operational mission permissions to OPERATOR (idempotent)
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000002', v.permission_code
FROM (VALUES
    ('mission.mission.read'),
    ('mission.mission.create'),
    ('mission.mission.pause'),
    ('mission.mission.cancel'),
    ('mission.mission.approve')
) AS v(permission_code)
WHERE NOT EXISTS (
    SELECT 1 FROM platform_iam.sys_role_permission rp
    WHERE rp.role_id = 'rol_01J00000000000000000000002'
      AND rp.permission_code = v.permission_code
);

-- Grant read-only mission permission to Viewer (idempotent)
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000003', 'mission.mission.read'
WHERE NOT EXISTS (
    SELECT 1 FROM platform_iam.sys_role_permission rp
    WHERE rp.role_id = 'rol_01J00000000000000000000003'
      AND rp.permission_code = 'mission.mission.read'
);
