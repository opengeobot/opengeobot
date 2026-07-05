-- Function: Create policy management tables for F-POLICY-001
-- Time: 2026-07-05
-- Author: AxeXie

-- Policy table (SM-POLICY-001 state machine: DRAFT → PUBLISHED → ARCHIVED)
CREATE TABLE policy.policy (
    id BIGSERIAL PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version INTEGER NOT NULL DEFAULT 0,
    scope VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

ALTER TABLE policy.policy ADD CONSTRAINT uq_policy_policy_id UNIQUE (policy_id);
ALTER TABLE policy.policy ADD CONSTRAINT uq_policy_name UNIQUE (name);
CREATE INDEX idx_policy_status ON policy.policy (status);
CREATE INDEX idx_policy_scope ON policy.policy (scope);

-- Policy rule table (versioned, immutable rule snapshots per published version)
CREATE TABLE policy.policy_rule (
    id BIGSERIAL PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    rule_type VARCHAR(128) NOT NULL,
    condition JSONB,
    action VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_policy_rule_policy ON policy.policy_rule (policy_id);
CREATE INDEX idx_policy_rule_version ON policy.policy_rule (policy_id, version);
CREATE INDEX idx_policy_rule_type ON policy.policy_rule (rule_type);

-- Policy assignment table (binds a policy to a scope instance)
CREATE TABLE policy.policy_assignment (
    id BIGSERIAL PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL,
    scope_type VARCHAR(64) NOT NULL,
    scope_id VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by VARCHAR(64)
);

ALTER TABLE policy.policy_assignment ADD CONSTRAINT uq_policy_assignment UNIQUE (policy_id, scope_type, scope_id);
CREATE INDEX idx_policy_assignment_policy ON policy.policy_assignment (policy_id);
CREATE INDEX idx_policy_assignment_scope ON policy.policy_assignment (scope_type, scope_id);
