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
  listPackages,
  uploadPackage,
  listCampaigns,
  createCampaign,
  getCampaign,
  rollback
} from '@/api/ota'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ota API', () => {
  it('listPackages gets /ota/packages with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listPackages({ page_number: 1, page_size: 20, type: 'FIRMWARE' })

    expect(mockClient.get).toHaveBeenCalledWith('/ota/packages', {
      params: { page: 1, page_size: 20, type: 'FIRMWARE' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('uploadPackage posts FormData to /ota/packages', async () => {
    const pkg = { package_id: 'p1', name: 'FW1', version: '1.0', type: 'FIRMWARE', file_path: '/p1', file_size: 1024, checksum: 'abc', created_at: '' }
    mockClient.post.mockResolvedValue({ data: pkg })
    const file = new File(['data'], 'fw.bin', { type: 'application/octet-stream' })
    const meta = { name: 'FW1', version: '1.0', type: 'FIRMWARE' as const, description: 'test' }

    const result = await uploadPackage(file, meta)

    expect(mockClient.post).toHaveBeenCalledWith(
      '/ota/packages',
      expect.any(FormData),
      expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: expect.any(Function)
      })
    )
    expect(result).toEqual(pkg)
  })

  it('listCampaigns gets /ota/campaigns with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listCampaigns({ page_number: 1, page_size: 20, status: 'IN_PROGRESS' })

    expect(mockClient.get).toHaveBeenCalledWith('/ota/campaigns', {
      params: { page: 1, page_size: 20, status: 'IN_PROGRESS' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('createCampaign posts to /ota/campaigns', async () => {
    const campaign = { campaign_id: 'c1', package_id: 'p1', canary_percent: 10, status: 'CREATED', target_robots: ['r1'], created_at: '' }
    mockClient.post.mockResolvedValue({ data: campaign })
    const data = { package_id: 'p1', target_robots: ['r1'], canary_percent: 10 }

    const result = await createCampaign(data)

    expect(mockClient.post).toHaveBeenCalledWith('/ota/campaigns', data)
    expect(result).toEqual(campaign)
  })

  it('getCampaign gets /ota/campaigns/{campaignId}', async () => {
    const detail = { campaign: { campaign_id: 'c1', package_id: 'p1', canary_percent: 10, status: 'COMPLETED', target_robots: [], created_at: '' }, deployments: [] }
    mockClient.get.mockResolvedValue({ data: detail })

    const result = await getCampaign('c1')

    expect(mockClient.get).toHaveBeenCalledWith('/ota/campaigns/c1')
    expect(result).toEqual(detail)
  })

  it('rollback posts to /ota/campaigns/{campaignId}/rollback', async () => {
    const campaign = { campaign_id: 'c1', package_id: 'p1', canary_percent: 10, status: 'ROLLED_BACK', target_robots: [], created_at: '' }
    mockClient.post.mockResolvedValue({ data: campaign })

    const result = await rollback('c1')

    expect(mockClient.post).toHaveBeenCalledWith('/ota/campaigns/c1/rollback')
    expect(result).toEqual(campaign)
  })
})
