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

/** GET /dict/types - paginated dict type list */
export async function listDictTypes(params: FilterParams): Promise<PageResult<DictType>> {
  const response = await client.get<PageResult<DictType>>('/dict/types', { params })
  return response.data
}

/** POST /dict/types - create a dict type */
export async function createDictType(data: CreateDictTypeRequest): Promise<DictType> {
  const response = await client.post<DictType>('/dict/types', data)
  return response.data
}

/** PUT /dict/types/{typeCode} - update a dict type */
export async function updateDictType(typeCode: string, data: UpdateDictTypeRequest): Promise<DictType> {
  const response = await client.put<DictType>(`/dict/types/${typeCode}`, data)
  return response.data
}

/** DELETE /dict/types/{typeCode} - delete a dict type */
export async function deleteDictType(typeCode: string): Promise<void> {
  await client.delete(`/dict/types/${typeCode}`)
}

/** GET /dict/types/{typeCode}/items - list items for a dict type (backend returns PageResponse) */
export async function listDictItems(typeCode: string): Promise<DictItem[]> {
  const response = await client.get<PageResult<DictItem>>(`/dict/types/${typeCode}/items`)
  return response.data.items
}

/** POST /dict/types/{typeCode}/items - create a dict item */
export async function createDictItem(typeCode: string, data: CreateDictItemRequest): Promise<DictItem> {
  const response = await client.post<DictItem>(`/dict/types/${typeCode}/items`, data)
  return response.data
}

/** PUT /dict/{typeCode}/items/{itemCode} - update a dict item */
export async function updateDictItem(typeCode: string, itemCode: string, data: UpdateDictItemRequest): Promise<DictItem> {
  const response = await client.put<DictItem>(`/dict/${typeCode}/items/${itemCode}`, data)
  return response.data
}

/** DELETE /dict/{typeCode}/items/{itemCode} - delete a dict item */
export async function deleteDictItem(typeCode: string, itemCode: string): Promise<void> {
  await client.delete(`/dict/${typeCode}/items/${itemCode}`)
}

/** POST /dict/types/{typeCode}/publish - publish a dict type version */
export async function publishDictType(typeCode: string): Promise<DictType> {
  const response = await client.post<DictType>(`/dict/types/${typeCode}/publish`)
  return response.data
}
