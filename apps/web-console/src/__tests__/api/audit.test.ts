import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/client', () => ({ default: mockClient }))

import { listAudits } from '@/api/audit'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('audit API', () => {
  it('listAudits gets /audits with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = {
      page_number: 1,
      page_size: 20,
      actor_id: 'u1',
      action: 'create',
      resource_type: 'user',
      trace_id: 't1',
      start_time: '2026-01-01',
      end_time: '2026-01-31'
    }

    const result = await listAudits(params)

    expect(mockClient.get).toHaveBeenCalledWith('/audits', { params })
    expect(result).toEqual(page)
  })
})
