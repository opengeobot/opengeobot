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
  listPermissions,
  listPermissionsByModule,
  getPermissionsByRole
} from '@/api/permission'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('permission API', () => {
  it('listPermissions gets /permissions', async () => {
    const perms = [
      { id: 'p1', permission_code: 'user:read', permission_name: 'Read', module: 'user', description: '' },
      { id: 'p2', permission_code: 'user:write', permission_name: 'Write', module: 'user', description: '' }
    ]
    mockClient.get.mockResolvedValue({ data: perms })

    const result = await listPermissions()

    expect(mockClient.get).toHaveBeenCalledWith('/permissions')
    expect(result).toEqual(perms)
  })

  it('listPermissionsByModule groups permissions by module', async () => {
    const perms = [
      { id: 'p1', permission_code: 'user:read', permission_name: 'Read', module: 'user', description: '' },
      { id: 'p2', permission_code: 'user:write', permission_name: 'Write', module: 'user', description: '' },
      { id: 'p3', permission_code: 'robot:read', permission_name: 'Read Robots', module: 'robot', description: '' }
    ]
    mockClient.get.mockResolvedValue({ data: perms })

    const result = await listPermissionsByModule()

    expect(mockClient.get).toHaveBeenCalledWith('/permissions')
    expect(result).toHaveLength(2)
    const userModule = result.find(g => g.module === 'user')
    expect(userModule?.permissions).toHaveLength(2)
    const robotModule = result.find(g => g.module === 'robot')
    expect(robotModule?.permissions).toHaveLength(1)
  })

  it('getPermissionsByRole gets /permissions/roles/{roleId}', async () => {
    const perms = [{ id: 'p1', permission_code: 'user:read', permission_name: 'Read', module: 'user', description: '' }]
    mockClient.get.mockResolvedValue({ data: perms })

    const result = await getPermissionsByRole('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/permissions/roles/r1')
    expect(result).toEqual(perms)
  })
})
