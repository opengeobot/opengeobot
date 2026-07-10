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
  id: 's1', skill_code: 'move', skill_name: 'Move', module: 'navigation',
  description: '', status: 'active', current_version: '1.0.0', created_at: ''
}

describe('skill API', () => {
  it('listSkills gets /skills with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listSkills({ page_number: 1, page_size: 20, module: 'navigation' })

    expect(mockClient.get).toHaveBeenCalledWith('/skills', { params: { page_number: 1, page_size: 20, module: 'navigation' } })
    expect(result).toEqual(page)
  })

  it('getSkill gets /skills/{id}', async () => {
    mockClient.get.mockResolvedValue({ data: mockSkill })

    const result = await getSkill('s1')

    expect(mockClient.get).toHaveBeenCalledWith('/skills/s1')
    expect(result).toEqual(mockSkill)
  })

  it('createSkill posts to /skills', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })
    const data = { skill_code: 'move', skill_name: 'Move', module: 'navigation', description: '' }

    const result = await createSkill(data)

    expect(mockClient.post).toHaveBeenCalledWith('/skills', data)
    expect(result).toEqual(mockSkill)
  })

  it('updateSkill puts to /skills/{id}', async () => {
    mockClient.put.mockResolvedValue({ data: mockSkill })

    const result = await updateSkill('s1', { skill_name: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/skills/s1', { skill_name: 'Updated' })
    expect(result).toEqual(mockSkill)
  })

  it('publishSkill posts to /skills/{id}/publish', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })

    const result = await publishSkill('s1')

    expect(mockClient.post).toHaveBeenCalledWith('/skills/s1/publish')
    expect(result).toEqual(mockSkill)
  })

  it('disableSkill posts to /skills/{id}/disable', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })

    const result = await disableSkill('s1')

    expect(mockClient.post).toHaveBeenCalledWith('/skills/s1/disable')
    expect(result).toEqual(mockSkill)
  })

  it('enableSkill posts to /skills/{id}/enable', async () => {
    mockClient.post.mockResolvedValue({ data: mockSkill })

    const result = await enableSkill('s1')

    expect(mockClient.post).toHaveBeenCalledWith('/skills/s1/enable')
    expect(result).toEqual(mockSkill)
  })

  it('getSkillVersions gets /skills/{id}/versions', async () => {
    const versions = [
      { id: 'v1', skill_id: 's1', version: '1.0.0', change_log: '', status: 'active', published_by: 'u1', published_at: '' }
    ]
    mockClient.get.mockResolvedValue({ data: versions })

    const result = await getSkillVersions('s1')

    expect(mockClient.get).toHaveBeenCalledWith('/skills/s1/versions')
    expect(result).toEqual(versions)
  })
})
