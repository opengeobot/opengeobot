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
  listI18nResources,
  createI18nResource,
  updateI18nResource,
  deleteI18nResource,
  batchImportI18n
} from '@/api/i18n'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('i18n API', () => {
  it('listI18nResources gets /i18n with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20, locale: 'zh-CN' }

    const result = await listI18nResources(params)

    expect(mockClient.get).toHaveBeenCalledWith('/i18n', { params })
    expect(result).toEqual(page)
  })

  it('createI18nResource posts to /i18n', async () => {
    const resource = { id: 'i1', resource_key: 'app.title', locale: 'zh-CN', resource_value: 'Title', module: 'common' }
    mockClient.post.mockResolvedValue({ data: resource })
    const data = { resource_key: 'app.title', locale: 'zh-CN', resource_value: 'Title', module: 'common' }

    const result = await createI18nResource(data)

    expect(mockClient.post).toHaveBeenCalledWith('/i18n', data)
    expect(result).toEqual(resource)
  })

  it('updateI18nResource puts to /i18n/{resourceKey} with locale param', async () => {
    const resource = { id: 'i1', resource_key: 'app.title', locale: 'zh-CN', resource_value: 'New', module: 'common' }
    mockClient.put.mockResolvedValue({ data: resource })

    const result = await updateI18nResource('app.title', 'zh-CN', { resource_value: 'New' })

    expect(mockClient.put).toHaveBeenCalledWith('/i18n/app.title', { resource_value: 'New' }, {
      params: { locale: 'zh-CN' }
    })
    expect(result).toEqual(resource)
  })

  it('deleteI18nResource deletes /i18n/{resourceKey} with locale param', async () => {
    mockClient.delete.mockResolvedValue({})

    await deleteI18nResource('app.title', 'zh-CN')

    expect(mockClient.delete).toHaveBeenCalledWith('/i18n/app.title', {
      params: { locale: 'zh-CN' }
    })
  })

  it('batchImportI18n posts FormData to /i18n/batch', async () => {
    const file = new File(['content'], 'i18n.csv', { type: 'text/csv' })
    mockClient.post.mockResolvedValue({ data: { imported: 10 } })

    const result = await batchImportI18n(file)

    expect(mockClient.post).toHaveBeenCalledWith(
      '/i18n/batch',
      expect.any(FormData),
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    expect(result).toEqual({ imported: 10 })
  })
})
