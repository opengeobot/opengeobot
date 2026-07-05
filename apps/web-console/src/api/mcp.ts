// Function: MCP tool registry and invocation API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  McpTool,
  McpInvocation,
  RegisterMcpToolRequest,
  InvokeMcpToolRequest,
  McpListParams,
  PageResult
} from '@/types/api'

/** GET /mcp/tools — paginated MCP tool list */
export async function listMcpTools(params: McpListParams): Promise<PageResult<McpTool>> {
  const response = await client.get<PageResult<McpTool>>('/mcp/tools', { params })
  return response.data
}

/** GET /mcp/tools/{id} — fetch a single MCP tool */
export async function getMcpTool(id: string): Promise<McpTool> {
  const response = await client.get<McpTool>(`/mcp/tools/${id}`)
  return response.data
}

/** POST /mcp/tools — register a new MCP tool */
export async function registerMcpTool(data: RegisterMcpToolRequest): Promise<McpTool> {
  const response = await client.post<McpTool>('/mcp/tools', data)
  return response.data
}

/** POST /mcp/tools/{id}/invoke — invoke an MCP tool */
export async function invokeMcpTool(id: string, data: InvokeMcpToolRequest): Promise<McpInvocation> {
  const response = await client.post<McpInvocation>(`/mcp/tools/${id}/invoke`, data)
  return response.data
}

/** GET /mcp/invocations — paginated invocation history */
export async function listInvocations(params: McpListParams): Promise<PageResult<McpInvocation>> {
  const response = await client.get<PageResult<McpInvocation>>('/mcp/invocations', { params })
  return response.data
}
