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
  listUsers,
  createUser,
  getUser,
  updateUser,
  updateUserStatus,
  getUserRoles,
  assignRoles
} from '@/api/user'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('user API', () => {
  it('listUsers gets /users with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20, org_id: 'org1' }

    const result = await listUsers(params)

    expect(mockClient.get).toHaveBeenCalledWith('/users', { params })
    expect(result).toEqual(page)
  })

  it('createUser posts to /users', async () => {
    const user = { id: 'u1', username: 'new', display_name: 'New', email: '', phone: '', org_id: 'o1', status: 'active', created_at: '' }
    mockClient.post.mockResolvedValue({ data: user })
    const data = { username: 'new', display_name: 'New', email: '', phone: '', password: 'pw', org_id: 'o1', role_ids: [] }

    const result = await createUser(data)

    expect(mockClient.post).toHaveBeenCalledWith('/users', data)
    expect(result).toEqual(user)
  })

  it('getUser gets /users/{id}', async () => {
    const user = { id: 'u1', username: 'admin', display_name: 'Admin', email: '', phone: '', org_id: 'o1', status: 'active', created_at: '' }
    mockClient.get.mockResolvedValue({ data: user })

    const result = await getUser('u1')

    expect(mockClient.get).toHaveBeenCalledWith('/users/u1')
    expect(result).toEqual(user)
  })

  it('updateUser puts to /users/{id}', async () => {
    const user = { id: 'u1', username: 'admin', display_name: 'Updated', email: '', phone: '', org_id: 'o1', status: 'active', created_at: '' }
    mockClient.put.mockResolvedValue({ data: user })
    const data = { display_name: 'Updated' }

    const result = await updateUser('u1', data)

    expect(mockClient.put).toHaveBeenCalledWith('/users/u1', data)
    expect(result).toEqual(user)
  })

  it('updateUserStatus patches /users/{id}/status', async () => {
    const user = { id: 'u1', username: 'admin', display_name: 'Admin', email: '', phone: '', org_id: 'o1', status: 'disabled', created_at: '' }
    mockClient.patch.mockResolvedValue({ data: user })

    const result = await updateUserStatus('u1', 'disabled')

    expect(mockClient.patch).toHaveBeenCalledWith('/users/u1/status', { status: 'disabled' })
    expect(result).toEqual(user)
  })

  it('getUserRoles gets /users/{id}/roles', async () => {
    const roles = [{ id: 'r1', role_name: 'Admin', role_code: 'admin', description: '', status: 'active', built_in: true }]
    mockClient.get.mockResolvedValue({ data: roles })

    const result = await getUserRoles('u1')

    expect(mockClient.get).toHaveBeenCalledWith('/users/u1/roles')
    expect(result).toEqual(roles)
  })

  it('assignRoles puts role_ids to /users/{id}/roles', async () => {
    mockClient.put.mockResolvedValue({})

    await assignRoles('u1', ['r1', 'r2'])

    expect(mockClient.put).toHaveBeenCalledWith('/users/u1/roles', { role_ids: ['r1', 'r2'] })
  })
})
