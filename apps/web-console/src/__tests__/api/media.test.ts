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
  uploadMedia,
  listMedia,
  getMedia,
  downloadMedia,
  deleteMedia
} from '@/api/media'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('media API', () => {
  it('uploadMedia posts FormData to /media/upload', async () => {
    const asset = { id: 'med1', file_name: 'img.png', mime_type: 'image/png', size: 1024, url: '/med1', thumbnail_url: null, uploaded_by: 'u1', created_at: '' }
    mockClient.post.mockResolvedValue({ data: asset })
    const file = new File(['data'], 'img.png', { type: 'image/png' })

    const result = await uploadMedia(file)

    expect(mockClient.post).toHaveBeenCalledWith(
      '/media/upload',
      expect.any(FormData),
      expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: expect.any(Function)
      })
    )
    expect(result).toEqual(asset)
  })

  it('uploadMedia calls onProgress callback', async () => {
    mockClient.post.mockImplementation((_url, _data, config) => {
      config.onUploadProgress({ loaded: 50, total: 100 })
      return Promise.resolve({ data: {} })
    })
    const file = new File(['data'], 'img.png', { type: 'image/png' })
    const onProgress = vi.fn()

    await uploadMedia(file, onProgress)

    expect(onProgress).toHaveBeenCalledWith(50)
  })

  it('listMedia gets /media with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listMedia({ page_number: 1, page_size: 20, mime_type: 'image/png' })

    expect(mockClient.get).toHaveBeenCalledWith('/media', { params: { page_number: 1, page_size: 20, mime_type: 'image/png' } })
    expect(result).toEqual(page)
  })

  it('getMedia gets /media/{id}', async () => {
    const asset = { id: 'med1', file_name: 'img.png', mime_type: 'image/png', size: 1024, url: '/med1', thumbnail_url: null, uploaded_by: 'u1', created_at: '' }
    mockClient.get.mockResolvedValue({ data: asset })

    const result = await getMedia('med1')

    expect(mockClient.get).toHaveBeenCalledWith('/media/med1')
    expect(result).toEqual(asset)
  })

  it('downloadMedia gets /media/{id}/download as blob', async () => {
    const blob = new Blob(['data'], { type: 'application/octet-stream' })
    mockClient.get.mockResolvedValue({ data: blob })

    const result = await downloadMedia('med1')

    expect(mockClient.get).toHaveBeenCalledWith('/media/med1/download', { responseType: 'blob' })
    expect(result).toBe(blob)
  })

  it('deleteMedia deletes /media/{id}', async () => {
    mockClient.delete.mockResolvedValue({})

    await deleteMedia('med1')

    expect(mockClient.delete).toHaveBeenCalledWith('/media/med1')
  })
})
