import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/client', () => ({ default: mockClient }))

import { listTraces, getTrace, getReplay } from '@/api/trace'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('trace API', () => {
  it('listTraces gets /traces with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listTraces({ page_number: 1, page_size: 20, resource_type: 'robot', status: 'ok' })

    expect(mockClient.get).toHaveBeenCalledWith('/traces', { params: { page_number: 1, page_size: 20, resource_type: 'robot', status: 'ok' } })
    expect(result).toEqual(page)
  })

  it('getTrace gets /traces/{id}', async () => {
    const trace = { id: 't1', trace_id: 'tr1', root_trace_id: 'tr1', operation: 'create', resource_type: 'robot', resource_id: 'r1', actor_id: 'u1', status: 'ok', started_at: '', finished_at: null, duration_ms: null }
    mockClient.get.mockResolvedValue({ data: trace })

    const result = await getTrace('t1')

    expect(mockClient.get).toHaveBeenCalledWith('/traces/t1')
    expect(result).toEqual(trace)
  })

  it('getReplay gets /traces/{id}/replay', async () => {
    const replay = { trace_id: 'tr1', spans: [], events: [] }
    mockClient.get.mockResolvedValue({ data: replay })

    const result = await getReplay('t1')

    expect(mockClient.get).toHaveBeenCalledWith('/traces/t1/replay')
    expect(result).toEqual(replay)
  })
})
