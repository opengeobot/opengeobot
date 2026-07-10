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
  listCases,
  getCase,
  listSuggestions,
  submitFeedback
} from '@/api/memory'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('memory API', () => {
  it('listCases gets /memory/cases with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listCases({ page_number: 1, page_size: 20, result: 'SUCCESS', robot_id: 'r1', skill_id: 's1' })

    expect(mockClient.get).toHaveBeenCalledWith('/memory/cases', {
      params: { page: 1, page_size: 20, result: 'SUCCESS', robot_id: 'r1', skill_id: 's1' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('getCase gets /memory/cases/{caseId}', async () => {
    const detail = { task_case: { case_id: 'c1', mission_id: 'm1', robot_id: 'r1', skill_id: 's1', result: 'SUCCESS', occurred_at: '' } }
    mockClient.get.mockResolvedValue({ data: detail })

    const result = await getCase('c1')

    expect(mockClient.get).toHaveBeenCalledWith('/memory/cases/c1')
    expect(result).toEqual(detail)
  })

  it('listSuggestions gets /memory/suggestions with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listSuggestions({ page_number: 1, page_size: 20, status: 'PENDING' })

    expect(mockClient.get).toHaveBeenCalledWith('/memory/suggestions', {
      params: { page: 1, page_size: 20, status: 'PENDING' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('submitFeedback posts to /memory/feedback', async () => {
    const suggestion = { suggestion_id: 's1', case_id: 'c1', suggestion_text: '', confidence: 0.9, status: 'ACCEPTED', feedback: 'ok', created_at: '' }
    mockClient.post.mockResolvedValue({ data: suggestion })
    const data = { suggestion_id: 's1', feedback: 'ok' }

    const result = await submitFeedback(data)

    expect(mockClient.post).toHaveBeenCalledWith('/memory/feedback', data)
    expect(result).toEqual(suggestion)
  })
})
