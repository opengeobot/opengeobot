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
  getOverview,
  getRobotMonitor,
  getMissionMonitor,
  takeover
} from '@/api/monitor'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('monitor API', () => {
  it('getOverview gets /monitor/overview', async () => {
    const overview = { total_robots: 10, online_robots: 8, busy_robots: 3, active_missions: 2, alerts: 1 }
    mockClient.get.mockResolvedValue({ data: overview })

    const result = await getOverview()

    expect(mockClient.get).toHaveBeenCalledWith('/monitor/overview')
    expect(result).toEqual(overview)
  })

  it('getRobotMonitor gets /monitor/robots/{robotId}', async () => {
    const robot = { robot_id: 'r1', robot_name: 'Robot1', status: 'online', battery: 80, position: { x: 1, y: 2, yaw: 0 }, current_mission_id: null, last_seen: '' }
    mockClient.get.mockResolvedValue({ data: robot })

    const result = await getRobotMonitor('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/monitor/robots/r1')
    expect(result).toEqual(robot)
  })

  it('getMissionMonitor gets /monitor/missions/{missionId}', async () => {
    const mission = { mission_id: 'm1', mission_name: 'M1', robot_name: 'R1', status: 'running', progress: 50, current_step: 2, total_steps: 4, trace_id: 't1' }
    mockClient.get.mockResolvedValue({ data: mission })

    const result = await getMissionMonitor('m1')

    expect(mockClient.get).toHaveBeenCalledWith('/monitor/missions/m1')
    expect(result).toEqual(mission)
  })

  it('takeover posts to /monitor/robots/{robotId}/takeover', async () => {
    const robot = { robot_id: 'r1', robot_name: 'Robot1', status: 'taken_over', battery: 80, position: { x: 1, y: 2, yaw: 0 }, current_mission_id: null, last_seen: '' }
    mockClient.post.mockResolvedValue({ data: robot })
    const data = { operator_id: 'u1', reason: 'manual' }

    const result = await takeover('r1', data)

    expect(mockClient.post).toHaveBeenCalledWith('/monitor/robots/r1/takeover', data)
    expect(result).toEqual(robot)
  })
})
