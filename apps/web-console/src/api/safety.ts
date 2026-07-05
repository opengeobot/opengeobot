// Function: Safety control and event query API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  SafetyState,
  SafetyEvent,
  EmergencyStopRequest,
  ResetSafetyRequest,
  SafetyEventListParams,
  PageResult
} from '@/types/api'

/** POST /safety/emergency-stop — trigger an emergency stop (latched locally) */
export async function emergencyStop(data?: EmergencyStopRequest): Promise<SafetyState> {
  const response = await client.post<SafetyState>('/safety/emergency-stop', data ?? {})
  return response.data
}

/** POST /safety/reset — reset the latched safety state */
export async function resetSafety(data?: ResetSafetyRequest): Promise<SafetyState> {
  const response = await client.post<SafetyState>('/safety/reset', data ?? {})
  return response.data
}

/** GET /safety/state — fetch current safety state */
export async function getSafetyState(robotId?: string): Promise<SafetyState> {
  const response = await client.get<SafetyState>('/safety/state', {
    params: robotId ? { robot_id: robotId } : undefined
  })
  return response.data
}

/** GET /safety/events — paginated safety event log */
export async function listSafetyEvents(params: SafetyEventListParams): Promise<PageResult<SafetyEvent>> {
  const response = await client.get<PageResult<SafetyEvent>>('/safety/events', { params })
  return response.data
}
