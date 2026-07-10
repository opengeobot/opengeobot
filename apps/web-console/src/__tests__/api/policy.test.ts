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
  listPolicies,
  getPolicy,
  createPolicy,
  updatePolicy,
  publishPolicy,
  listPolicyVersions
} from '@/api/policy'

beforeEach(() => {
  vi.clearAllMocks()
})

const mockPolicy = {
  id: 'p1', policy_code: 'P1', policy_name: 'Policy1', description: '',
  status: 'active', version: 1, scope: 'global', rules: {}, created_at: ''
}

describe('policy API', () => {
  it('listPolicies gets /policies with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listPolicies({ page_number: 1, page_size: 20, status: 'active', scope: 'global' })

    expect(mockClient.get).toHaveBeenCalledWith('/policies', { params: { page_number: 1, page_size: 20, status: 'active', scope: 'global' } })
    expect(result).toEqual(page)
  })

  it('getPolicy gets /policies/{id}', async () => {
    mockClient.get.mockResolvedValue({ data: mockPolicy })

    const result = await getPolicy('p1')

    expect(mockClient.get).toHaveBeenCalledWith('/policies/p1')
    expect(result).toEqual(mockPolicy)
  })

  it('createPolicy posts to /policies', async () => {
    mockClient.post.mockResolvedValue({ data: mockPolicy })
    const data = { policy_code: 'P1', policy_name: 'Policy1', description: '', scope: 'global', rules: {} }

    const result = await createPolicy(data)

    expect(mockClient.post).toHaveBeenCalledWith('/policies', data)
    expect(result).toEqual(mockPolicy)
  })

  it('updatePolicy puts to /policies/{id}', async () => {
    mockClient.put.mockResolvedValue({ data: mockPolicy })

    const result = await updatePolicy('p1', { policy_name: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/policies/p1', { policy_name: 'Updated' })
    expect(result).toEqual(mockPolicy)
  })

  it('publishPolicy posts to /policies/{id}/publish', async () => {
    mockClient.post.mockResolvedValue({ data: mockPolicy })

    const result = await publishPolicy('p1')

    expect(mockClient.post).toHaveBeenCalledWith('/policies/p1/publish')
    expect(result).toEqual(mockPolicy)
  })

  it('listPolicyVersions gets /policies/{id}/versions', async () => {
    const versions = [
      { id: 'v1', policy_id: 'p1', version: 1, rules: {}, status: 'active', published_by: 'u1', published_at: '' }
    ]
    mockClient.get.mockResolvedValue({ data: versions })

    const result = await listPolicyVersions('p1')

    expect(mockClient.get).toHaveBeenCalledWith('/policies/p1/versions')
    expect(result).toEqual(versions)
  })
})
