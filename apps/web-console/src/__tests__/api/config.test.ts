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
  listConfigs,
  getConfig,
  createConfig,
  updateConfig,
  getConfigHistory
} from '@/api/config'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('config API', () => {
  it('listConfigs gets /configs with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20 }

    const result = await listConfigs(params)

    expect(mockClient.get).toHaveBeenCalledWith('/configs', { params })
    expect(result).toEqual(page)
  })

  it('getConfig gets /configs/{id}', async () => {
    const config = { id: 'c1', config_key: 'timeout', config_value: '30', value_type: 'int', module: 'system', description: '', version: 1 }
    mockClient.get.mockResolvedValue({ data: config })

    const result = await getConfig('c1')

    expect(mockClient.get).toHaveBeenCalledWith('/configs/c1')
    expect(result).toEqual(config)
  })

  it('createConfig posts to /configs', async () => {
    const config = { id: 'c1', config_key: 'timeout', config_value: '30', value_type: 'int', module: 'system', description: '', version: 1 }
    mockClient.post.mockResolvedValue({ data: config })
    const data = { config_key: 'timeout', config_value: '30', value_type: 'int', module: 'system', description: '' }

    const result = await createConfig(data)

    expect(mockClient.post).toHaveBeenCalledWith('/configs', data)
    expect(result).toEqual(config)
  })

  it('updateConfig puts to /configs/{id}', async () => {
    const config = { id: 'c1', config_key: 'timeout', config_value: '60', value_type: 'int', module: 'system', description: '', version: 2 }
    mockClient.put.mockResolvedValue({ data: config })

    const result = await updateConfig('c1', { config_value: '60' })

    expect(mockClient.put).toHaveBeenCalledWith('/configs/c1', { config_value: '60' })
    expect(result).toEqual(config)
  })

  it('getConfigHistory gets /configs/{id}/history', async () => {
    const history = [
      { id: 'h1', config_id: 'c1', config_value: '30', value_type: 'int', version: 1, updated_by: 'u1', updated_at: '2026-01-01' }
    ]
    mockClient.get.mockResolvedValue({ data: history })

    const result = await getConfigHistory('c1')

    expect(mockClient.get).toHaveBeenCalledWith('/configs/c1/history')
    expect(result).toEqual(history)
  })
})
