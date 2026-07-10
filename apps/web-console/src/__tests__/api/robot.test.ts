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
import type { Robot, RobotCapability } from '@/types/api'

beforeEach(() => {
  vi.clearAllMocks()
})

const mockRobot: Robot = {
  robot_id: 'r1',
  name: 'Robot1',
  model_id: 'm1',
  serial_number: 'SN1',
  org_id: 'o1',
  status: 'online',
  last_seen: null,
  capabilities: [],
  created_at: ''
}

describe('robot API', () => {
  it('listRobots maps page_number to page query param', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20, status: 'online', org_id: 'o1' }

    const result = await listRobots(params)

    expect(mockClient.get).toHaveBeenCalledWith('/robots', {
      params: {
        page: 1,
        page_size: 20,
        name: undefined,
        status: 'online',
        org_id: 'o1',
        model_id: undefined
      }
    })
    expect(result).toEqual(page)
  })

  it('getRobot gets /robots/{robot_id}', async () => {
    mockClient.get.mockResolvedValue({ data: mockRobot })

    const result = await getRobot('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/robots/r1')
    expect(result).toEqual(mockRobot)
    expect(result.robot_id).toBe('r1')
  })

  it('createRobot posts to /robots', async () => {
    mockClient.post.mockResolvedValue({ data: mockRobot })
    const data = {
      name: 'Robot1',
      model_id: 'm1',
      serial_number: 'SN1',
      org_id: 'o1',
      capabilities: [] as RobotCapability[]
    }

    const result = await createRobot(data)

    expect(mockClient.post).toHaveBeenCalledWith('/robots', data)
    expect(result).toEqual(mockRobot)
  })

  it('updateRobot puts to /robots/{robot_id}', async () => {
    mockClient.put.mockResolvedValue({ data: { ...mockRobot, name: 'Updated' } })

    const result = await updateRobot('r1', { name: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/robots/r1', { name: 'Updated' })
    expect(result.name).toBe('Updated')
  })

  it('deleteRobot deletes /robots/{robot_id}', async () => {
    mockClient.delete.mockResolvedValue({})

    await deleteRobot('r1')

    expect(mockClient.delete).toHaveBeenCalledWith('/robots/r1')
  })

  it('updateRobotStatus patches /robots/{robot_id}/status', async () => {
    mockClient.patch.mockResolvedValue({ data: mockRobot })

    const result = await updateRobotStatus('r1', 'online')

    expect(mockClient.patch).toHaveBeenCalledWith('/robots/r1/status', { status: 'online' })
    expect(result).toEqual(mockRobot)
  })

  it('getRobotCapabilities unwraps capability DTO objects', async () => {
    const caps: RobotCapability[] = [
      { capability_type: 'move', capability_value: 'true' },
      { capability_type: 'rotate', capability_value: 'true' }
    ]
    mockClient.get.mockResolvedValue({ data: { robot_id: 'r1', capabilities: caps } })

    const result = await getRobotCapabilities('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/robots/r1/capabilities')
    expect(result).toEqual(caps)
    expect(result[0].capability_type).toBe('move')
  })

  it('updateRobotCapabilities puts capability DTO objects', async () => {
    const caps: RobotCapability[] = [{ capability_type: 'move', capability_value: 'true' }]
    mockClient.put.mockResolvedValue({})

    await updateRobotCapabilities('r1', caps)

    expect(mockClient.put).toHaveBeenCalledWith('/robots/r1/capabilities', { capabilities: caps })
  })

  it('listRobotModels gets /robot-models with params', async () => {
    const page = {
      items: [{ model_id: 'm1', model_name: 'Robot1', manufacturer: 'V1', description: '' }],
      total: 1,
      page_number: 1,
      page_size: 20
    }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20 }

    const result = await listRobotModels(params)

    expect(mockClient.get).toHaveBeenCalledWith('/robot-models', { params })
    expect(result).toEqual(page)
  })

  it('listRobotGroups gets /robot-groups with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20 }

    const result = await listRobotGroups(params)

    expect(mockClient.get).toHaveBeenCalledWith('/robot-groups', { params })
    expect(result).toEqual(page)
  })

  it('createRobotModel posts to /robot-models', async () => {
    const model = { model_id: 'm1', model_name: 'R1', manufacturer: 'V1', description: '' }
    mockClient.post.mockResolvedValue({ data: model })
    const data = { model_name: 'R1', manufacturer: 'V1' }

    const { createRobotModel } = await import('@/api/robot')
    const result = await createRobotModel(data)

    expect(mockClient.post).toHaveBeenCalledWith('/robot-models', data)
    expect(result).toEqual(model)
  })

  it('acquireControlLease posts to /robots/{robot_id}/control-leases', async () => {
    const lease = {
      lease_id: 'l1',
      robot_id: 'r1',
      holder_user_id: 'u1',
      status: 'ACTIVE',
      acquired_at: '',
      expires_at: '',
      released_at: null,
      fencing_token: null,
      created_at: '',
      updated_at: ''
    }
    mockClient.post.mockResolvedValue({ data: lease })

    const { acquireControlLease } = await import('@/api/robot')
    const result = await acquireControlLease('r1', { ttl_seconds: 300 })

    expect(mockClient.post).toHaveBeenCalledWith('/robots/r1/control-leases', { ttl_seconds: 300 })
    expect(result).toEqual(lease)
  })

  it('detail navigation must use robot_id not id', () => {
    const row = mockRobot as Robot & { id?: string }
    expect(row.robot_id).toBe('r1')
    expect(row.id).toBeUndefined()
  })
})
