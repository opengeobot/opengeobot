import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/client', () => ({ default: mockClient }))

import { createExport, getExport, downloadExport } from '@/api/export'

beforeEach(() => {
  vi.clearAllMocks()
  Object.defineProperty(URL, 'createObjectURL', {
    value: vi.fn(() => 'blob:url'),
    writable: true,
    configurable: true
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    value: vi.fn(),
    writable: true,
    configurable: true
  })
})

describe('export API', () => {
  it('createExport posts to /exports', async () => {
    const task = { id: 'e1', status: 'PENDING', file_url: null, created_at: '' }
    mockClient.post.mockResolvedValue({ data: task })
    const data = { resource_type: 'users', filters: {} }

    const result = await createExport(data)

    expect(mockClient.post).toHaveBeenCalledWith('/exports', data)
    expect(result).toEqual(task)
  })

  it('getExport gets /exports/{id}', async () => {
    const task = { id: 'e1', status: 'COMPLETED', file_url: '/files/e1', created_at: '' }
    mockClient.get.mockResolvedValue({ data: task })

    const result = await getExport('e1')

    expect(mockClient.get).toHaveBeenCalledWith('/exports/e1')
    expect(result).toEqual(task)
  })

  it('downloadExport gets blob from /exports/{id}/download', async () => {
    const blob = new Blob(['data'], { type: 'text/csv' })
    mockClient.get.mockResolvedValue({
      data: blob,
      headers: { 'content-type': 'text/csv' }
    })

    await downloadExport('e1', 'export.csv')

    expect(mockClient.get).toHaveBeenCalledWith('/exports/e1/download', { responseType: 'blob' })
    expect(URL.createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:url')
  })
})
