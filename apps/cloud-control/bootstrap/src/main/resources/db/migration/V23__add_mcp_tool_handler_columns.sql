-- Function: Add handler routing columns to mcp_tool for real tool execution
-- Time: 2026-07-06
-- Author: AxeXie

-- handler_type: how the tool is invoked (NATS = edge request-reply, HTTP = HTTP endpoint, NULL = not configured)
ALTER TABLE skill_registry.mcp_tool ADD COLUMN IF NOT EXISTS handler_type VARCHAR(32);
-- handler_endpoint: NATS subject or HTTP URL that receives the tool invocation
ALTER TABLE skill_registry.mcp_tool ADD COLUMN IF NOT EXISTS handler_endpoint VARCHAR(512);
