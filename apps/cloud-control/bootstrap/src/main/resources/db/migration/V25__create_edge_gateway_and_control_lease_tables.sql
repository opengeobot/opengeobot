-- Function: Create edge gateway and control lease tables for F-EDGE-001 / F-MONITOR-001
-- Time: 2026-07-10
-- Author: AxeXie

-- Edge gateway identity and connection (robot_registry owns edge_gateway per manifest)
CREATE TABLE robot_registry.edge_gateway (
    id BIGSERIAL PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    certificate_fingerprint VARCHAR(128),
    certificate_expires_at TIMESTAMPTZ,
    runtime_version VARCHAR(64),
    bound_robot_id VARCHAR(64),
    last_heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE robot_registry.edge_gateway IS '边缘网关身份与连接状态（F-EDGE-001）';
COMMENT ON COLUMN robot_registry.edge_gateway.id IS '内部自增主键';
COMMENT ON COLUMN robot_registry.edge_gateway.gateway_id IS '网关对外公开标识（ULID）';
COMMENT ON COLUMN robot_registry.edge_gateway.name IS '网关显示名称';
COMMENT ON COLUMN robot_registry.edge_gateway.org_id IS '所属组织公开标识';
COMMENT ON COLUMN robot_registry.edge_gateway.status IS '身份状态：REGISTERED/ACTIVE/DEGRADED/REVOKED';
COMMENT ON COLUMN robot_registry.edge_gateway.certificate_fingerprint IS '当前有效证书指纹';
COMMENT ON COLUMN robot_registry.edge_gateway.certificate_expires_at IS '当前证书过期时间（UTC）';
COMMENT ON COLUMN robot_registry.edge_gateway.runtime_version IS '边缘运行时版本';
COMMENT ON COLUMN robot_registry.edge_gateway.bound_robot_id IS '绑定机器人公开标识，可为空';
COMMENT ON COLUMN robot_registry.edge_gateway.last_heartbeat_at IS '最近一次心跳时间（UTC）';
COMMENT ON COLUMN robot_registry.edge_gateway.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN robot_registry.edge_gateway.updated_at IS '最后更新时间（UTC）';

ALTER TABLE robot_registry.edge_gateway ADD CONSTRAINT uq_edge_gateway_id UNIQUE (gateway_id);
CREATE INDEX idx_edge_gateway_org ON robot_registry.edge_gateway (org_id);
CREATE INDEX idx_edge_gateway_status ON robot_registry.edge_gateway (status);
CREATE INDEX idx_edge_gateway_bound_robot ON robot_registry.edge_gateway (bound_robot_id);

-- Edge gateway certificate history
CREATE TABLE robot_registry.edge_gateway_certificate (
    id BIGSERIAL PRIMARY KEY,
    cert_id VARCHAR(64) NOT NULL,
    gateway_id VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE robot_registry.edge_gateway_certificate IS '边缘网关证书历史与轮换记录（F-EDGE-001）';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.id IS '内部自增主键';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.cert_id IS '证书记录公开标识（ULID）';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.gateway_id IS '所属网关公开标识';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.fingerprint IS '证书指纹';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.issued_at IS '证书签发时间（UTC）';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.expires_at IS '证书过期时间（UTC）';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.status IS '证书状态：ACTIVE/ROTATED/REVOKED';
COMMENT ON COLUMN robot_registry.edge_gateway_certificate.created_at IS '记录创建时间（UTC）';

ALTER TABLE robot_registry.edge_gateway_certificate ADD CONSTRAINT uq_edge_gateway_cert_id UNIQUE (cert_id);
CREATE INDEX idx_edge_gateway_cert_gateway ON robot_registry.edge_gateway_certificate (gateway_id);
CREATE INDEX idx_edge_gateway_cert_status ON robot_registry.edge_gateway_certificate (status);
CREATE INDEX idx_edge_gateway_cert_fingerprint ON robot_registry.edge_gateway_certificate (fingerprint);

ALTER TABLE robot_registry.edge_gateway_certificate
    ADD CONSTRAINT fk_edge_gateway_cert_gateway
    FOREIGN KEY (gateway_id) REFERENCES robot_registry.edge_gateway (gateway_id);

-- Control lease for manual takeover (fleet owns control_lease per manifest)
CREATE TABLE fleet.control_lease (
    id BIGSERIAL PRIMARY KEY,
    lease_id VARCHAR(64) NOT NULL,
    robot_id VARCHAR(64) NOT NULL,
    holder_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    acquired_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    fencing_token VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE fleet.control_lease IS '机器人人工接管控制租约（F-MONITOR-001 / SM-CONTROL-001）';
COMMENT ON COLUMN fleet.control_lease.id IS '内部自增主键';
COMMENT ON COLUMN fleet.control_lease.lease_id IS '租约公开标识（ULID）';
COMMENT ON COLUMN fleet.control_lease.robot_id IS '被接管机器人公开标识';
COMMENT ON COLUMN fleet.control_lease.holder_user_id IS '租约持有用户公开标识';
COMMENT ON COLUMN fleet.control_lease.status IS '租约状态：ACTIVE/RELEASED/EXPIRED/REVOKED';
COMMENT ON COLUMN fleet.control_lease.acquired_at IS '租约获取时间（UTC）';
COMMENT ON COLUMN fleet.control_lease.expires_at IS '租约过期时间（UTC）';
COMMENT ON COLUMN fleet.control_lease.released_at IS '租约释放/撤销时间（UTC）';
COMMENT ON COLUMN fleet.control_lease.fencing_token IS 'fencing token，ACTIVE 租约唯一';
COMMENT ON COLUMN fleet.control_lease.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN fleet.control_lease.updated_at IS '最后更新时间（UTC）';

ALTER TABLE fleet.control_lease ADD CONSTRAINT uq_control_lease_id UNIQUE (lease_id);
ALTER TABLE fleet.control_lease ADD CONSTRAINT uq_control_lease_fencing_token UNIQUE (fencing_token);
CREATE INDEX idx_control_lease_robot ON fleet.control_lease (robot_id);
CREATE INDEX idx_control_lease_status ON fleet.control_lease (status);
CREATE INDEX idx_control_lease_holder ON fleet.control_lease (holder_user_id);
CREATE INDEX idx_control_lease_expires ON fleet.control_lease (expires_at);

-- Seed: sample edge gateway bound to Pioneer-01
INSERT INTO robot_registry.edge_gateway (
    gateway_id, name, org_id, status, certificate_fingerprint,
    certificate_expires_at, runtime_version, bound_robot_id, last_heartbeat_at
) VALUES (
    'gw_01J00000000000000000000001',
    'Edge-Gateway-Pioneer-01',
    'org_01J00000000000000000000001',
    'ACTIVE',
    'sha256:sample-fingerprint-pioneer-01',
    NOW() + INTERVAL '365 days',
    '0.1.0',
    'rbt_01J00000000000000000000001',
    NOW()
);

INSERT INTO robot_registry.edge_gateway_certificate (
    cert_id, gateway_id, fingerprint, issued_at, expires_at, status
) VALUES (
    'cert_01J00000000000000000000001',
    'gw_01J00000000000000000000001',
    'sha256:sample-fingerprint-pioneer-01',
    NOW(),
    NOW() + INTERVAL '365 days',
    'ACTIVE'
);

-- Seed permissions if missing (V4 only seeded platform IAM permissions)
INSERT INTO platform_iam.sys_permission (permission_code, permission_name, module, description, resource_type, action)
SELECT v.permission_code, v.permission_name, v.module, v.description, v.resource_type, v.action
FROM (VALUES
    ('edge.gateway.read', 'View Edge Gateways', 'edge', 'View edge gateway list and detail', 'gateway', 'read'),
    ('edge.gateway.manage', 'Manage Edge Gateways', 'edge', 'Register, activate and revoke edge gateways', 'gateway', 'manage'),
    ('edge.gateway.certificate.rotate', 'Rotate Edge Gateway Certificate', 'edge', 'Rotate edge gateway mTLS certificates', 'gateway', 'certificate.rotate'),
    ('robot.robot.control', 'Control Robot', 'robot', 'Acquire and release robot control leases', 'robot', 'control')
) AS v(permission_code, permission_name, module, description, resource_type, action)
WHERE NOT EXISTS (
    SELECT 1 FROM platform_iam.sys_permission p WHERE p.permission_code = v.permission_code
);

-- Grant new permissions to SYS_ADMIN
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000001', v.permission_code
FROM (VALUES
    ('edge.gateway.read'),
    ('edge.gateway.manage'),
    ('edge.gateway.certificate.rotate'),
    ('robot.robot.control')
) AS v(permission_code)
WHERE NOT EXISTS (
    SELECT 1 FROM platform_iam.sys_role_permission rp
    WHERE rp.role_id = 'rol_01J00000000000000000000001'
      AND rp.permission_code = v.permission_code
);

-- Grant read permission to Viewer
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000003', 'edge.gateway.read'
WHERE NOT EXISTS (
    SELECT 1 FROM platform_iam.sys_role_permission rp
    WHERE rp.role_id = 'rol_01J00000000000000000000003'
      AND rp.permission_code = 'edge.gateway.read'
);
