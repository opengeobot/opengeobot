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
  emergencyStop,
  resetSafety,
  getSafetyState,
  listSafetyEvents
} from '@/api/safety'

beforeEach(() => {
  vi.clearAllMocks()
})

const mockState = {
  robot_id: null, e_stopped: false, locked: false, reason: '', last_event_id: null, updated_at: ''
}

describe('safety API', () => {
  it('emergencyStop posts to /safety/emergency-stop with data', async () => {
    mockClient.post.mockResolvedValue({ data: mockState })
    const data = { robot_id: 'r1', reason: 'manual' }

    const result = await emergencyStop(data)

    expect(mockClient.post).toHaveBeenCalledWith('/safety/emergency-stop', data)
    expect(result).toEqual(mockState)
  })

  it('emergencyStop sends empty object when no data', async () => {
    mockClient.post.mockResolvedValue({ data: mockState })

    await emergencyStop()

    expect(mockClient.post).toHaveBeenCalledWith('/safety/emergency-stop', {})
  })

  it('resetSafety posts to /safety/reset with data', async () => {
    mockClient.post.mockResolvedValue({ data: mockState })
    const data = { robot_id: 'r1' }

    const result = await resetSafety(data)

    expect(mockClient.post).toHaveBeenCalledWith('/safety/reset', data)
    expect(result).toEqual(mockState)
  })

  it('resetSafety sends empty object when no data', async () => {
    mockClient.post.mockResolvedValue({ data: mockState })

    await resetSafety()

    expect(mockClient.post).toHaveBeenCalledWith('/safety/reset', {})
  })

  it('getSafetyState gets /safety/state with robot_id param', async () => {
    mockClient.get.mockResolvedValue({ data: mockState })

    const result = await getSafetyState('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/safety/state', { params: { robot_id: 'r1' } })
    expect(result).toEqual(mockState)
  })

  it('getSafetyState gets /safety/state without params when no robotId', async () => {
    mockClient.get.mockResolvedValue({ data: mockState })

    await getSafetyState()

    expect(mockClient.get).toHaveBeenCalledWith('/safety/state', { params: undefined })
  })

  it('listSafetyEvents gets /safety/events with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listSafetyEvents({ page_number: 1, page_size: 20, robot_id: 'r1', level: 'critical' })

    expect(mockClient.get).toHaveBeenCalledWith('/safety/events', { params: { page_number: 1, page_size: 20, robot_id: 'r1', level: 'critical' } })
    expect(result).toEqual(page)
  })
})
