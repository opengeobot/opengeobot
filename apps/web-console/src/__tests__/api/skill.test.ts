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
  listSkills,
  getSkill,
  createSkill,
  updateSkill,
  publishSkill,
  disableSkill,
  enableSkill,
  getSkillVersions
} from '@/api/skill'

beforeEach(() => {
  vi.clearAllMocks()
})

const mockSkill = {
  skill_id: 's1',
  name: 'Move',
  module: 'navigation',
  description: '',
  status: 'active',
  current_version: 1,
  created_at: ''
}

describe('skill API', () => {
  it('listSkills maps page_number to page query param', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listSkills({ page_number: 1, page_size: 20, module: 'navigation' })

    expect(mockClient.get).toHaveBeenCalledWith('/skills', {
      params: { page: 1, page_size: 20, module: 'navigation', status: undefined }
    })
    expect(result).toEqual(page)
  })

  it('getSkill gets /skills/{skill_id}', async () => {
    mockClient.get.mockResolvedValue({ data: mockSkill })

    const result = await getSkill('s1')

    expect(mockClient.get).toHaveBeenCalledWith('/skills/s1')
    expect(result).toEqual(mockSkill)
    expect(result.skill_id).toBe('s1')
  })

  it('createSkill posts to /skills with name field', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })
    const data = { name: 'Move', module: 'navigation', description: '' }

    const result = await createSkill(data)

    expect(mockClient.post).toHaveBeenCalledWith('/skills', data)
    expect(result).toEqual(mockSkill)
  })

  it('updateSkill puts to /skills/{skill_id}', async () => {
    mockClient.put.mockResolvedValue({ data: mockSkill })

    const result = await updateSkill('s1', { description: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/skills/s1', { description: 'Updated' })
    expect(result).toEqual(mockSkill)
  })

  it('publishSkill posts to /skills/{skill_id}/publish', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })

    const result = await publishSkill('s1')

    expect(mockClient.post).toHaveBeenCalledWith('/skills/s1/publish')
    expect(result).toEqual(mockSkill)
  })

  it('disableSkill posts to /skills/{skill_id}/disable', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })

    const result = await disableSkill('s1')

    expect(mockClient.post).toHaveBeenCalledWith('/skills/s1/disable')
    expect(result).toEqual(mockSkill)
  })

  it('enableSkill posts to /skills/{skill_id}/enable', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })

    const result = await enableSkill('s1')

    expect(mockClient.post).toHaveBeenCalledWith('/skills/s1/enable')
    expect(result).toEqual(mockSkill)
  })

  it('getSkillVersions gets /skills/{skill_id}/versions', async () => {
    const versions = [
      { skill_id: 's1', version: 1, change_log: '', status: 'active', published_by: 'u1', published_at: '' }
    ]
    mockClient.get.mockResolvedValue({ data: versions })

    const result = await getSkillVersions('s1')

    expect(mockClient.get).toHaveBeenCalledWith('/skills/s1/versions')
    expect(result).toEqual(versions)
  })
})
