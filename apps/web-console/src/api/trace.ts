// Function: Trace query and replay API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  Trace,
  TraceReplay,
  TraceListParams,
  PageResult
} from '@/types/api'

/** GET /traces — paginated trace list */
export async function listTraces(params: TraceListParams): Promise<PageResult<Trace>> {
  const response = await client.get<PageResult<Trace>>('/traces', { params })
  return response.data
}

/** GET /traces/{id} — fetch a single trace */
export async function getTrace(id: string): Promise<Trace> {
  const response = await client.get<Trace>(`/traces/${id}`)
  return response.data
}

/** GET /traces/{id}/replay — fetch trace replay (spans and events) */
export async function getReplay(id: string): Promise<TraceReplay> {
  const response = await client.get<TraceReplay>(`/traces/${id}/replay`)
  return response.data
}
