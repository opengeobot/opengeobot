// Function: Fleet scheduling, conflict and failover API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  FleetSchedule,
  ConflictRecord,
  FailoverEvent,
  CreateScheduleRequest,
  ResolveConflictRequest,
  TriggerFailoverRequest,
  FleetScheduleListParams,
  ConflictListParams,
  FailoverListParams,
  PageResult
} from '@/types/api'

/** GET /fleet/schedule — paginated fleet schedule list */
export async function listSchedules(params: FleetScheduleListParams): Promise<PageResult<FleetSchedule>> {
  const response = await client.get<{ items: FleetSchedule[]; total: number; page: number; page_size: number }>(
    '/fleet/schedule',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        status: params.status,
        robot_id: params.robot_id,
        mission_id: params.mission_id
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /fleet/schedule — create a fleet schedule */
export async function createSchedule(data: CreateScheduleRequest): Promise<FleetSchedule> {
  const response = await client.post<FleetSchedule>('/fleet/schedule', data)
  return response.data
}

/** GET /fleet/conflicts — paginated conflict list */
export async function listConflicts(params: ConflictListParams): Promise<PageResult<ConflictRecord>> {
  const response = await client.get<{ items: ConflictRecord[]; total: number; page: number; page_size: number }>(
    '/fleet/conflicts',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        status: params.status
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /fleet/conflicts/{conflictId}/resolve — resolve a conflict */
export async function resolveConflict(conflictId: string, data: ResolveConflictRequest): Promise<ConflictRecord> {
  const response = await client.post<ConflictRecord>(`/fleet/conflicts/${conflictId}/resolve`, data)
  return response.data
}

/** GET /fleet/failovers — paginated failover event list */
export async function listFailovers(params: FailoverListParams): Promise<PageResult<FailoverEvent>> {
  const response = await client.get<{ items: FailoverEvent[]; total: number; page: number; page_size: number }>(
    '/fleet/failovers',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        robot_id: params.robot_id,
        status: params.status
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /fleet/failovers — trigger a manual failover */
export async function triggerFailover(data: TriggerFailoverRequest): Promise<FailoverEvent> {
  const response = await client.post<FailoverEvent>('/fleet/failovers', data)
  return response.data
}
