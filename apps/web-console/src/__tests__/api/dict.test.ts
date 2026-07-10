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
  listDictTypes,
  createDictType,
  updateDictType,
  deleteDictType,
  listDictItems,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  publishDictType
} from '@/api/dict'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('dict API', () => {
  it('listDictTypes gets /dict/types with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20 }

    const result = await listDictTypes(params)

    expect(mockClient.get).toHaveBeenCalledWith('/dict/types', { params })
    expect(result).toEqual(page)
  })

  it('createDictType posts to /dict/types', async () => {
    const dictType = { id: 'd1', type_code: 'status', type_name: 'Status', status: 'active', version: 1 }
    mockClient.post.mockResolvedValue({ data: dictType })
    const data = { type_code: 'status', type_name: 'Status' }

    const result = await createDictType(data)

    expect(mockClient.post).toHaveBeenCalledWith('/dict/types', data)
    expect(result).toEqual(dictType)
  })

  it('updateDictType puts to /dict/types/{typeCode}', async () => {
    const dictType = { id: 'd1', type_code: 'status', type_name: 'Updated', status: 'active', version: 1 }
    mockClient.put.mockResolvedValue({ data: dictType })

    const result = await updateDictType('status', { type_name: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/dict/types/status', { type_name: 'Updated' })
    expect(result).toEqual(dictType)
  })

  it('deleteDictType deletes /dict/types/{typeCode}', async () => {
    mockClient.delete.mockResolvedValue({})

    await deleteDictType('status')

    expect(mockClient.delete).toHaveBeenCalledWith('/dict/types/status')
  })

  it('listDictItems gets items from /dict/types/{typeCode}/items and unwraps items', async () => {
    const page = {
      items: [
        { id: 'i1', type_id: 'd1', item_code: 'a', item_value: 'A', label_zh_cn: 'A', label_en_us: 'A', sort_order: 1, status: 'active' }
      ],
      total: 1,
      page_number: 1,
      page_size: 20
    }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listDictItems('status')

    expect(mockClient.get).toHaveBeenCalledWith('/dict/types/status/items')
    expect(result).toEqual(page.items)
  })

  it('createDictItem posts to /dict/types/{typeCode}/items', async () => {
    const item = { id: 'i1', type_id: 'd1', item_code: 'a', item_value: 'A', label_zh_cn: 'A', label_en_us: 'A', sort_order: 1, status: 'active' }
    mockClient.post.mockResolvedValue({ data: item })
    const data = { item_code: 'a', item_value: 'A', label_zh_cn: 'A', label_en_us: 'A', sort_order: 1 }

    const result = await createDictItem('status', data)

    expect(mockClient.post).toHaveBeenCalledWith('/dict/types/status/items', data)
    expect(result).toEqual(item)
  })

  it('updateDictItem puts to /dict/{typeCode}/items/{itemCode}', async () => {
    const item = { id: 'i1', type_id: 'd1', item_code: 'a', item_value: 'B', label_zh_cn: 'B', label_en_us: 'B', sort_order: 1, status: 'active' }
    mockClient.put.mockResolvedValue({ data: item })

    const result = await updateDictItem('status', 'a', { item_value: 'B' })

    expect(mockClient.put).toHaveBeenCalledWith('/dict/status/items/a', { item_value: 'B' })
    expect(result).toEqual(item)
  })

  it('deleteDictItem deletes /dict/{typeCode}/items/{itemCode}', async () => {
    mockClient.delete.mockResolvedValue({})

    await deleteDictItem('status', 'a')

    expect(mockClient.delete).toHaveBeenCalledWith('/dict/status/items/a')
  })

  it('publishDictType posts to /dict/types/{typeCode}/publish', async () => {
    const dictType = { id: 'd1', type_code: 'status', type_name: 'Status', status: 'active', version: 2 }
    mockClient.post.mockResolvedValue({ data: dictType })

    const result = await publishDictType('status')

    expect(mockClient.post).toHaveBeenCalledWith('/dict/types/status/publish')
    expect(result).toEqual(dictType)
  })
})
