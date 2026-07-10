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
  listRoles,
  createRole,
  getRole,
  updateRole,
  getRolePermissions,
  assignPermissions
} from '@/api/role'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('role API', () => {
  it('listRoles gets /roles with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20 }

    const result = await listRoles(params)

    expect(mockClient.get).toHaveBeenCalledWith('/roles', { params })
    expect(result).toEqual(page)
  })

  it('createRole posts to /roles', async () => {
    const role = { id: 'r1', role_name: 'Editor', role_code: 'editor', description: '', status: 'active', built_in: false }
    mockClient.post.mockResolvedValue({ data: role })
    const data = { role_name: 'Editor', role_code: 'editor', description: '' }

    const result = await createRole(data)

    expect(mockClient.post).toHaveBeenCalledWith('/roles', data)
    expect(result).toEqual(role)
  })

  it('getRole gets /roles/{id}', async () => {
    const role = { id: 'r1', role_name: 'Admin', role_code: 'admin', description: '', status: 'active', built_in: true }
    mockClient.get.mockResolvedValue({ data: role })

    const result = await getRole('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/roles/r1')
    expect(result).toEqual(role)
  })

  it('updateRole puts to /roles/{id}', async () => {
    const role = { id: 'r1', role_name: 'Updated', role_code: 'admin', description: '', status: 'active', built_in: true }
    mockClient.put.mockResolvedValue({ data: role })
    const data = { role_name: 'Updated' }

    const result = await updateRole('r1', data)

    expect(mockClient.put).toHaveBeenCalledWith('/roles/r1', data)
    expect(result).toEqual(role)
  })

  it('getRolePermissions gets /roles/{id}/permissions', async () => {
    const perms = [{ id: 'p1', permission_code: 'user:read', permission_name: 'Read Users', module: 'user', description: '' }]
    mockClient.get.mockResolvedValue({ data: perms })

    const result = await getRolePermissions('r1')

    expect(mockClient.get).toHaveBeenCalledWith('/roles/r1/permissions')
    expect(result).toEqual(perms)
  })

  it('assignPermissions puts permission_codes to /roles/{id}/permissions', async () => {
    mockClient.put.mockResolvedValue({})

    await assignPermissions('r1', ['user:read', 'user:write'])

    expect(mockClient.put).toHaveBeenCalledWith('/roles/r1/permissions', {
      permission_codes: ['user:read', 'user:write']
    })
  })
})
