// Function: Mission management and approval API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  Mission,
  MissionTemplate,
  CreateMissionRequest,
  UpdateMissionRequest,
  RevisePlanRequest,
  CreateMissionTemplateRequest,
  RejectMissionRequest,
  MissionListParams,
  PageResult
} from '@/types/api'

/** GET /missions — paginated mission list */
export async function listMissions(params: MissionListParams): Promise<PageResult<Mission>> {
  const response = await client.get<PageResult<Mission>>('/missions', { params })
  return response.data
}

/** GET /missions/{id} — fetch a single mission with steps */
export async function getMission(id: string): Promise<Mission> {
  const response = await client.get<Mission>(`/missions/${id}`)
  return response.data
}

/** POST /missions — create a new mission */
export async function createMission(data: CreateMissionRequest): Promise<Mission> {
  const response = await client.post<Mission>('/missions', data)
  return response.data
}

/** PUT /missions/{id} — update a mission */
export async function updateMission(id: string, data: UpdateMissionRequest): Promise<Mission> {
  const response = await client.put<Mission>(`/missions/${id}`, data)
  return response.data
}

/** POST /missions/{id}/revise — revise the mission plan (steps) */
export async function revisePlan(id: string, data: RevisePlanRequest): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/revise`, data)
  return response.data
}

/** POST /missions/{id}/start — start a mission */
export async function startMission(id: string): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/start`)
  return response.data
}

/** POST /missions/{id}/pause — pause a running mission */
export async function pauseMission(id: string): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/pause`)
  return response.data
}

/** POST /missions/{id}/resume — resume a paused mission */
export async function resumeMission(id: string): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/resume`)
  return response.data
}

/** POST /missions/{id}/cancel — cancel a mission */
export async function cancelMission(id: string): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/cancel`)
  return response.data
}

/** GET /mission-templates — paginated mission template list */
export async function listMissionTemplates(params: MissionListParams): Promise<PageResult<MissionTemplate>> {
  const response = await client.get<PageResult<MissionTemplate>>('/mission-templates', { params })
  return response.data
}

/** POST /mission-templates — create a mission template */
export async function createMissionTemplate(data: CreateMissionTemplateRequest): Promise<MissionTemplate> {
  const response = await client.post<MissionTemplate>('/mission-templates', data)
  return response.data
}

/** POST /missions/{id}/submit-approval — submit a mission for approval */
export async function submitApproval(id: string): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/submit-approval`)
  return response.data
}

/** POST /missions/{id}/approve — approve a mission */
export async function approveMission(id: string): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/approve`)
  return response.data
}

/** POST /missions/{id}/reject — reject a mission */
export async function rejectMission(id: string, data: RejectMissionRequest): Promise<Mission> {
  const response = await client.post<Mission>(`/missions/${id}/reject`, data)
  return response.data
}
