import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/client', () => ({ default: mockClient }))

import { listOrgs, createOrg, getOrg, updateOrg, deleteOrg } from '@/api/org'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('org API', () => {
  it('listOrgs gets /orgs', async () => {
    const orgs = [{ id: 'o1', org_code: 'ROOT', org_name: 'Root', parent_id: null, description: '', status: 'active' }]
    mockClient.get.mockResolvedValue({ data: orgs })

    const result = await listOrgs()

    expect(mockClient.get).toHaveBeenCalledWith('/orgs')
    expect(result).toEqual(orgs)
  })

  it('createOrg posts to /orgs', async () => {
    const org = { id: 'o2', org_code: 'CHILD', org_name: 'Child', parent_id: 'o1', description: '', status: 'active' }
    mockClient.post.mockResolvedValue({ data: org })
    const data = { org_code: 'CHILD', org_name: 'Child', parent_id: 'o1', description: '' }

    const result = await createOrg(data)

    expect(mockClient.post).toHaveBeenCalledWith('/orgs', data)
    expect(result).toEqual(org)
  })

  it('getOrg gets /orgs/{id}', async () => {
    const org = { id: 'o1', org_code: 'ROOT', org_name: 'Root', parent_id: null, description: '', status: 'active' }
    mockClient.get.mockResolvedValue({ data: org })

    const result = await getOrg('o1')

    expect(mockClient.get).toHaveBeenCalledWith('/orgs/o1')
    expect(result).toEqual(org)
  })

  it('updateOrg puts to /orgs/{id}', async () => {
    const org = { id: 'o1', org_code: 'ROOT', org_name: 'Updated', parent_id: null, description: '', status: 'active' }
    mockClient.put.mockResolvedValue({ data: org })
    const data = { org_name: 'Updated' }

    const result = await updateOrg('o1', data)

    expect(mockClient.put).toHaveBeenCalledWith('/orgs/o1', data)
    expect(result).toEqual(org)
  })

  it('deleteOrg deletes /orgs/{id}', async () => {
    mockClient.delete.mockResolvedValue({})

    await deleteOrg('o1')

    expect(mockClient.delete).toHaveBeenCalledWith('/orgs/o1')
  })
})
