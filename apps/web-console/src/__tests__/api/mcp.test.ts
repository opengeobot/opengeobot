import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/client', () => ({ default: mockClient }))

import {
  listMcpTools,
  getMcpTool,
  registerMcpTool,
  invokeMcpTool,
  listInvocations
} from '@/api/mcp'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('mcp API', () => {
  it('listMcpTools gets /mcp/tools with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listMcpTools({ page_number: 1, page_size: 20, status: 'active' })

    expect(mockClient.get).toHaveBeenCalledWith('/mcp/tools', { params: { page_number: 1, page_size: 20, status: 'active' } })
    expect(result).toEqual(page)
  })

  it('getMcpTool gets /mcp/tools/{id}', async () => {
    const tool = { id: 'tool1', tool_code: 'move', tool_name: 'Move', description: '', input_schema: {}, status: 'active', registered_at: '' }
    mockClient.get.mockResolvedValue({ data: tool })

    const result = await getMcpTool('tool1')

    expect(mockClient.get).toHaveBeenCalledWith('/mcp/tools/tool1')
    expect(result).toEqual(tool)
  })

  it('registerMcpTool posts to /mcp/tools', async () => {
    const tool = { id: 'tool1', tool_code: 'move', tool_name: 'Move', description: '', input_schema: {}, status: 'active', registered_at: '' }
    mockClient.post.mockResolvedValue({ data: tool })
    const data = { tool_code: 'move', tool_name: 'Move', description: '', input_schema: {} }

    const result = await registerMcpTool(data)

    expect(mockClient.post).toHaveBeenCalledWith('/mcp/tools', data)
    expect(result).toEqual(tool)
  })

  it('invokeMcpTool posts to /mcp/tools/{id}/invoke', async () => {
    const invocation = { id: 'inv1', tool_id: 'tool1', tool_name: 'Move', trace_id: 't1', caller: 'u1', status: 'success', input: null, output: null, error: null, started_at: '', finished_at: null }
    mockClient.post.mockResolvedValue({ data: invocation })
    const data = { input: { target: 'loc1' }, trace_id: 't1' }

    const result = await invokeMcpTool('tool1', data)

    expect(mockClient.post).toHaveBeenCalledWith('/mcp/tools/tool1/invoke', data)
    expect(result).toEqual(invocation)
  })

  it('listInvocations gets /mcp/invocations with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listInvocations({ page_number: 1, page_size: 20 })

    expect(mockClient.get).toHaveBeenCalledWith('/mcp/invocations', { params: { page_number: 1, page_size: 20 } })
    expect(result).toEqual(page)
  })
})
