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
  listMaps,
  getMap,
  createMap,
  updateMap,
  publishMap,
  listAreas,
  createArea,
  listRestrictedAreas,
  createRestrictedArea
} from '@/api/map'

beforeEach(() => {
  vi.clearAllMocks()
})

const mockMap = {
  id: 'map1', map_code: 'M1', map_name: 'Map1', status: 'active',
  version: 1, frame: 'map', description: '', created_at: ''
}

describe('map API', () => {
  it('listMaps gets /maps with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listMaps({ page_number: 1, page_size: 20, status: 'active' })

    expect(mockClient.get).toHaveBeenCalledWith('/maps', { params: { page_number: 1, page_size: 20, status: 'active' } })
    expect(result).toEqual(page)
  })

  it('getMap gets /maps/{id}', async () => {
    mockClient.get.mockResolvedValue({ data: mockMap })

    const result = await getMap('map1')

    expect(mockClient.get).toHaveBeenCalledWith('/maps/map1')
    expect(result).toEqual(mockMap)
  })

  it('createMap posts to /maps', async () => {
    mockClient.post.mockResolvedValue({ data: mockMap })
    const data = { map_code: 'M1', map_name: 'Map1', frame: 'map', description: '' }

    const result = await createMap(data)

    expect(mockClient.post).toHaveBeenCalledWith('/maps', data)
    expect(result).toEqual(mockMap)
  })

  it('updateMap puts to /maps/{id}', async () => {
    mockClient.put.mockResolvedValue({ data: mockMap })

    const result = await updateMap('map1', { map_name: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/maps/map1', { map_name: 'Updated' })
    expect(result).toEqual(mockMap)
  })

  it('publishMap posts to /maps/{id}/publish', async () => {
    mockClient.post.mockResolvedValue({ data: mockMap })

    const result = await publishMap('map1')

    expect(mockClient.post).toHaveBeenCalledWith('/maps/map1/publish')
    expect(result).toEqual(mockMap)
  })

  it('listAreas gets /maps/{mapId}/areas', async () => {
    const areas = [{ id: 'a1', map_id: 'map1', area_code: 'A1', area_name: 'Area1', area_type: 'zone', polygon: {} }]
    mockClient.get.mockResolvedValue({ data: areas })

    const result = await listAreas('map1')

    expect(mockClient.get).toHaveBeenCalledWith('/maps/map1/areas')
    expect(result).toEqual(areas)
  })

  it('createArea posts to /maps/{mapId}/areas', async () => {
    const area = { id: 'a1', map_id: 'map1', area_code: 'A1', area_name: 'Area1', area_type: 'zone', polygon: {} }
    mockClient.post.mockResolvedValue({ data: area })
    const data = { area_code: 'A1', area_name: 'Area1', area_type: 'zone', polygon: {} }

    const result = await createArea('map1', data)

    expect(mockClient.post).toHaveBeenCalledWith('/maps/map1/areas', data)
    expect(result).toEqual(area)
  })

  it('listRestrictedAreas gets /maps/{mapId}/restricted-areas', async () => {
    const areas = [{ id: 'ra1', map_id: 'map1', area_code: 'RA1', area_name: 'Restricted1', level: 'high', polygon: {} }]
    mockClient.get.mockResolvedValue({ data: areas })

    const result = await listRestrictedAreas('map1')

    expect(mockClient.get).toHaveBeenCalledWith('/maps/map1/restricted-areas')
    expect(result).toEqual(areas)
  })

  it('createRestrictedArea posts to /maps/{mapId}/restricted-areas', async () => {
    const area = { id: 'ra1', map_id: 'map1', area_code: 'RA1', area_name: 'Restricted1', level: 'high', polygon: {} }
    mockClient.post.mockResolvedValue({ data: area })
    const data = { area_code: 'RA1', area_name: 'Restricted1', level: 'high', polygon: {} }

    const result = await createRestrictedArea('map1', data)

    expect(mockClient.post).toHaveBeenCalledWith('/maps/map1/restricted-areas', data)
    expect(result).toEqual(area)
  })
})
