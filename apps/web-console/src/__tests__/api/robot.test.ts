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
  listRobots,
  getRobot,
  createRobot,
  updateRobot,
  deleteRobot,
  updateRobotStatus,
  getRobotCapabilities,
  updateRobotCapabilities,
  listRobotModels,
  listRobotGroups
} from '@/api/robot'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('robot API', () => {
  it('listRobots gets /robots with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20, status: 'online', org_id: 'o1' }

    const result = await listRobots(params)

    expect(mockClient.get).toHaveBeenCalledWith('/robots', { params })
    expect(result).toEqual(page)
  })

  it('getRobot gets /robots/{id}', async () => {
    const robot = { id: 'r1', name: 'Robot1', model_id: 'm1', serial_number: 'SN1', org_id: 'o1', status: 'online', last_seen: null, capabilities: [], created_at: '' }
    mockClient.get.mockResolvedValue({ data: robot })

    const result = await getRobot('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/robots/r1')
    expect(result).toEqual(robot)
  })

  it('createRobot posts to /robots', async () => {
    const robot = { id: 'r1', name: 'Robot1', model_id: 'm1', serial_number: 'SN1', org_id: 'o1', status: 'offline', last_seen: null, capabilities: [], created_at: '' }
    mockClient.post.mockResolvedValue({ data: robot })
    const data = { name: 'Robot1', model_id: 'm1', serial_number: 'SN1', org_id: 'o1', capabilities: [] }

    const result = await createRobot(data)

    expect(mockClient.post).toHaveBeenCalledWith('/robots', data)
    expect(result).toEqual(robot)
  })

  it('updateRobot puts to /robots/{id}', async () => {
    const robot = { id: 'r1', name: 'Updated', model_id: 'm1', serial_number: 'SN1', org_id: 'o1', status: 'offline', last_seen: null, capabilities: [], created_at: '' }
    mockClient.put.mockResolvedValue({ data: robot })

    const result = await updateRobot('r1', { name: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/robots/r1', { name: 'Updated' })
    expect(result).toEqual(robot)
  })

  it('deleteRobot deletes /robots/{id}', async () => {
    mockClient.delete.mockResolvedValue({})

    await deleteRobot('r1')

    expect(mockClient.delete).toHaveBeenCalledWith('/robots/r1')
  })

  it('updateRobotStatus patches /robots/{id}/status', async () => {
    const robot = { id: 'r1', name: 'Robot1', model_id: 'm1', serial_number: 'SN1', org_id: 'o1', status: 'online', last_seen: null, capabilities: [], created_at: '' }
    mockClient.patch.mockResolvedValue({ data: robot })

    const result = await updateRobotStatus('r1', 'online')

    expect(mockClient.patch).toHaveBeenCalledWith('/robots/r1/status', { status: 'online' })
    expect(result).toEqual(robot)
  })

  it('getRobotCapabilities gets /robots/{id}/capabilities', async () => {
    mockClient.get.mockResolvedValue({ data: ['move', 'rotate'] })

    const result = await getRobotCapabilities('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/robots/r1/capabilities')
    expect(result).toEqual(['move', 'rotate'])
  })

  it('updateRobotCapabilities puts to /robots/{id}/capabilities', async () => {
    mockClient.put.mockResolvedValue({})

    await updateRobotCapabilities('r1', ['move', 'rotate'])

    expect(mockClient.put).toHaveBeenCalledWith('/robots/r1/capabilities', { capabilities: ['move', 'rotate'] })
  })

  it('listRobotModels gets /robot-models', async () => {
    const models = [{ id: 'm1', model_code: 'R1', model_name: 'Robot1', vendor: 'V1', description: '' }]
    mockClient.get.mockResolvedValue({ data: models })

    const result = await listRobotModels()

    expect(mockClient.get).toHaveBeenCalledWith('/robot-models')
    expect(result).toEqual(models)
  })

  it('listRobotGroups gets /robot-groups with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20 }

    const result = await listRobotGroups(params)

    expect(mockClient.get).toHaveBeenCalledWith('/robot-groups', { params })
    expect(result).toEqual(page)
  })
})
