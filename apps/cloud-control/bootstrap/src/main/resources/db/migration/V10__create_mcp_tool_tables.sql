-- Function: Create MCP tool gateway tables for F-MCP-001
-- Time: 2026-07-05
-- Author: AxeXie

-- MCP tool table (registered tool contract, lifecycle: DRAFT → ACTIVE → DEPRECATED/DISABLED)
CREATE TABLE skill_registry.mcp_tool (
    id BIGSERIAL PRIMARY KEY,
    tool_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    input_schema JSONB,
    output_schema JSONB,
    canary_percent INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64)
);

ALTER TABLE skill_registry.mcp_tool ADD CONSTRAINT uq_mcp_tool_tool_id UNIQUE (tool_id);
ALTER TABLE skill_registry.mcp_tool ADD CONSTRAINT uq_mcp_tool_name UNIQUE (name);
ALTER TABLE skill_registry.mcp_tool ADD CONSTRAINT ck_mcp_tool_canary_percent CHECK (canary_percent >= 0 AND canary_percent <= 100);
CREATE INDEX idx_mcp_tool_status ON skill_registry.mcp_tool (status);

-- MCP invocation log table (append-only, audit-grade invocation history)
CREATE TABLE skill_registry.mcp_invocation_log (
    id BIGSERIAL PRIMARY KEY,
    invocation_id VARCHAR(64) NOT NULL,
    tool_id VARCHAR(64) NOT NULL,
    input_params JSONB,
    output_result JSONB,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    duration_ms INTEGER,
    invoked_by VARCHAR(64),
    invoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(64)
);

ALTER TABLE skill_registry.mcp_invocation_log ADD CONSTRAINT uq_mcp_invocation_id UNIQUE (invocation_id);
CREATE INDEX idx_mcp_invocation_tool ON skill_registry.mcp_invocation_log (tool_id);
CREATE INDEX idx_mcp_invocation_status ON skill_registry.mcp_invocation_log (status);
CREATE INDEX idx_mcp_invocation_invoked_at ON skill_registry.mcp_invocation_log (invoked_at);
