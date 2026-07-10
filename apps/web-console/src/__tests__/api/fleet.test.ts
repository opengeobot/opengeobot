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
  listSchedules,
  createSchedule,
  listConflicts,
  resolveConflict,
  listFailovers,
  triggerFailover
} from '@/api/fleet'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('fleet API', () => {
  it('listSchedules gets /fleet/schedule with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listSchedules({ page_number: 1, page_size: 20, status: 'ACTIVE', robot_id: 'r1', mission_id: 'm1' })

    expect(mockClient.get).toHaveBeenCalledWith('/fleet/schedule', {
      params: { page: 1, page_size: 20, status: 'ACTIVE', robot_id: 'r1', mission_id: 'm1' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('createSchedule posts to /fleet/schedule', async () => {
    const schedule = { schedule_id: 's1', mission_id: 'm1', robot_id: 'r1', planned_start: '', planned_end: '', priority: 'NORMAL', status: 'PENDING', created_at: '' }
    mockClient.post.mockResolvedValue({ data: schedule })
    const data = { mission_id: 'm1', robot_id: 'r1', planned_start: '', planned_end: '', priority: 'NORMAL' as const }

    const result = await createSchedule(data)

    expect(mockClient.post).toHaveBeenCalledWith('/fleet/schedule', data)
    expect(result).toEqual(schedule)
  })

  it('listConflicts gets /fleet/conflicts with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listConflicts({ page_number: 1, page_size: 20, status: 'OPEN' })

    expect(mockClient.get).toHaveBeenCalledWith('/fleet/conflicts', {
      params: { page: 1, page_size: 20, status: 'OPEN' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('resolveConflict posts to /fleet/conflicts/{conflictId}/resolve', async () => {
    const conflict = { conflict_id: 'c1', schedule_ids: [], conflict_type: 'TIME_OVERLAP', status: 'RESOLVED', resolution: 'REORDER', detected_at: '' }
    mockClient.post.mockResolvedValue({ data: conflict })
    const data = { resolution: 'REORDER' as const }

    const result = await resolveConflict('c1', data)

    expect(mockClient.post).toHaveBeenCalledWith('/fleet/conflicts/c1/resolve', data)
    expect(result).toEqual(conflict)
  })

  it('listFailovers gets /fleet/failovers with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listFailovers({ page_number: 1, page_size: 20, robot_id: 'r1', status: 'COMPLETED' })

    expect(mockClient.get).toHaveBeenCalledWith('/fleet/failovers', {
      params: { page: 1, page_size: 20, robot_id: 'r1', status: 'COMPLETED' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('triggerFailover posts to /fleet/failovers', async () => {
    const event = { failover_id: 'f1', robot_id: 'r1', mission_id: 'm1', reason: 'failure', status: 'INITIATED', occurred_at: '' }
    mockClient.post.mockResolvedValue({ data: event })
    const data = { robot_id: 'r1', mission_id: 'm1', reason: 'failure' }

    const result = await triggerFailover(data)

    expect(mockClient.post).toHaveBeenCalledWith('/fleet/failovers', data)
    expect(result).toEqual(event)
  })
})
