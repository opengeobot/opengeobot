// Function: Dictionary management API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type {
  DictType,
  DictItem,
  CreateDictTypeRequest,
  UpdateDictTypeRequest,
  CreateDictItemRequest,
  UpdateDictItemRequest,
  FilterParams,
  PageResult
} from '@/types/api'

/** GET /dict/types — paginated dict type list */
export async function listDictTypes(params: FilterParams): Promise<PageResult<DictType>> {
  const response = await client.get<PageResult<DictType>>('/dict/types', { params })
  return response.data
}

/** POST /dict/types — create a dict type */
export async function createDictType(data: CreateDictTypeRequest): Promise<DictType> {
  const response = await client.post<DictType>('/dict/types', data)
  return response.data
}

/** PUT /dict/types/{id} — update a dict type */
export async function updateDictType(id: string, data: UpdateDictTypeRequest): Promise<DictType> {
  const response = await client.put<DictType>(`/dict/types/${id}`, data)
  return response.data
}

/** DELETE /dict/types/{id} — delete a dict type */
export async function deleteDictType(id: string): Promise<void> {
  await client.delete(`/dict/types/${id}`)
}

/** GET /dict/types/{id}/items — list items for a dict type */
export async function listDictItems(typeId: string): Promise<DictItem[]> {
  const response = await client.get<DictItem[]>(`/dict/types/${typeId}/items`)
  return response.data
}

/** POST /dict/items — create a dict item */
export async function createDictItem(data: CreateDictItemRequest): Promise<DictItem> {
  const response = await client.post<DictItem>('/dict/items', data)
  return response.data
}

/** PUT /dict/items/{id} — update a dict item */
export async function updateDictItem(id: string, data: UpdateDictItemRequest): Promise<DictItem> {
  const response = await client.put<DictItem>(`/dict/items/${id}`, data)
  return response.data
}

/** DELETE /dict/items/{id} — delete a dict item */
export async function deleteDictItem(id: string): Promise<void> {
  await client.delete(`/dict/items/${id}`)
}

/** POST /dict/types/{id}/publish — publish a dict type version */
export async function publishDictType(id: string): Promise<DictType> {
  const response = await client.post<DictType>(`/dict/types/${id}/publish`)
  return response.data
}
