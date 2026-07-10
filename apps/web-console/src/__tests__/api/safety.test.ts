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
import type { SafetyState } from '@/types/api'

beforeEach(() => {
  vi.clearAllMocks()
})

const mockState: SafetyState = {
  robot_id: null,
  state: 'NORMAL',
  reason: null,
  updated_at: ''
}

/** Mirrors SafetyControlView derived flags from SM-SAFETY state. */
function deriveSafetyFlags(state: SafetyState): { eStopped: boolean; locked: boolean } {
  return {
    eStopped: state.state === 'EMERGENCY_STOPPED',
    locked: state.state === 'EMERGENCY_STOPPED' || state.state === 'RESETTING'
  }
}

describe('safety API', () => {
  it('emergencyStop posts to /safety/emergency-stop with data', async () => {
    mockClient.post.mockResolvedValue({ data: { ...mockState, state: 'EMERGENCY_STOPPED' } })
    const data = { robot_id: 'r1', reason: 'manual' }

    const result = await emergencyStop(data)

    expect(mockClient.post).toHaveBeenCalledWith('/safety/emergency-stop', data)
    expect(result.state).toBe('EMERGENCY_STOPPED')
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

    const result = await listSafetyEvents({
      page_number: 1,
      page_size: 20,
      robot_id: 'r1',
      level: 'critical'
    })

    expect(mockClient.get).toHaveBeenCalledWith('/safety/events', {
      params: { page_number: 1, page_size: 20, robot_id: 'r1', level: 'critical' }
    })
    expect(result).toEqual(page)
  })

  it('derives eStopped and locked from state enum', () => {
    expect(deriveSafetyFlags({ ...mockState, state: 'NORMAL' })).toEqual({
      eStopped: false,
      locked: false
    })
    expect(deriveSafetyFlags({ ...mockState, state: 'EMERGENCY_STOPPED' })).toEqual({
      eStopped: true,
      locked: true
    })
    expect(deriveSafetyFlags({ ...mockState, state: 'RESETTING' })).toEqual({
      eStopped: false,
      locked: true
    })
  })
})
