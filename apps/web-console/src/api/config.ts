// Function: Platform config management API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type {
  Config,
  ConfigHistory,
  CreateConfigRequest,
  UpdateConfigRequest,
  FilterParams,
  PageResult
} from '@/types/api'

/** GET /configs — paginated config list */
export async function listConfigs(params: FilterParams): Promise<PageResult<Config>> {
  const response = await client.get<PageResult<Config>>('/configs', { params })
  return response.data
}

/** GET /configs/{id} — fetch a single config */
export async function getConfig(id: string): Promise<Config> {
  const response = await client.get<Config>(`/configs/${id}`)
  return response.data
}

/** POST /configs — create a config */
export async function createConfig(data: CreateConfigRequest): Promise<Config> {
  const response = await client.post<Config>('/configs', data)
  return response.data
}

/** PUT /configs/{id} — update a config */
export async function updateConfig(id: string, data: UpdateConfigRequest): Promise<Config> {
  const response = await client.put<Config>(`/configs/${id}`, data)
  return response.data
}

/** GET /configs/{id}/history — fetch config version history */
export async function getConfigHistory(id: string): Promise<ConfigHistory[]> {
  const response = await client.get<ConfigHistory[]>(`/configs/${id}/history`)
  return response.data
}
