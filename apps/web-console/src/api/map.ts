// Function: Map, area and restricted area management API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  GameMap,
  MapArea,
  RestrictedArea,
  CreateMapRequest,
  UpdateMapRequest,
  CreateAreaRequest,
  CreateRestrictedAreaRequest,
  MapListParams,
  PageResult
} from '@/types/api'

/** GET /maps — paginated map list */
export async function listMaps(params: MapListParams): Promise<PageResult<GameMap>> {
  const response = await client.get<PageResult<GameMap>>('/maps', { params })
  return response.data
}

/** GET /maps/{id} — fetch a single map */
export async function getMap(id: string): Promise<GameMap> {
  const response = await client.get<GameMap>(`/maps/${id}`)
  return response.data
}

/** POST /maps — create a new map */
export async function createMap(data: CreateMapRequest): Promise<GameMap> {
  const response = await client.post<GameMap>('/maps', data)
  return response.data
}

/** PUT /maps/{id} — update a map */
export async function updateMap(id: string, data: UpdateMapRequest): Promise<GameMap> {
  const response = await client.put<GameMap>(`/maps/${id}`, data)
  return response.data
}

/** POST /maps/{id}/publish — publish a map (generates a new version) */
export async function publishMap(id: string): Promise<GameMap> {
  const response = await client.post<GameMap>(`/maps/${id}/publish`)
  return response.data
}

/** GET /maps/{mapId}/areas — list areas of a map */
export async function listAreas(mapId: string): Promise<MapArea[]> {
  const response = await client.get<MapArea[]>(`/maps/${mapId}/areas`)
  return response.data
}

/** POST /maps/{mapId}/areas — create an area on a map */
export async function createArea(mapId: string, data: CreateAreaRequest): Promise<MapArea> {
  const response = await client.post<MapArea>(`/maps/${mapId}/areas`, data)
  return response.data
}

/** GET /maps/{mapId}/restricted-areas — list restricted areas of a map */
export async function listRestrictedAreas(mapId: string): Promise<RestrictedArea[]> {
  const response = await client.get<RestrictedArea[]>(`/maps/${mapId}/restricted-areas`)
  return response.data
}

/** POST /maps/{mapId}/restricted-areas — create a restricted area on a map */
export async function createRestrictedArea(mapId: string, data: CreateRestrictedAreaRequest): Promise<RestrictedArea> {
  const response = await client.post<RestrictedArea>(`/maps/${mapId}/restricted-areas`, data)
  return response.data
}
