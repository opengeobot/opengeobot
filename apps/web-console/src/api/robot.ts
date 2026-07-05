// Function: Robot management API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  Robot,
  RobotModel,
  RobotGroup,
  CreateRobotRequest,
  UpdateRobotRequest,
  RobotListParams,
  PageResult
} from '@/types/api'

/** GET /robots — paginated robot list with filters */
export async function listRobots(params: RobotListParams): Promise<PageResult<Robot>> {
  const response = await client.get<PageResult<Robot>>('/robots', { params })
  return response.data
}

/** GET /robots/{id} — fetch a single robot */
export async function getRobot(id: string): Promise<Robot> {
  const response = await client.get<Robot>(`/robots/${id}`)
  return response.data
}

/** POST /robots — register a new robot */
export async function createRobot(data: CreateRobotRequest): Promise<Robot> {
  const response = await client.post<Robot>('/robots', data)
  return response.data
}

/** PUT /robots/{id} — update a robot */
export async function updateRobot(id: string, data: UpdateRobotRequest): Promise<Robot> {
  const response = await client.put<Robot>(`/robots/${id}`, data)
  return response.data
}

/** DELETE /robots/{id} — remove a robot */
export async function deleteRobot(id: string): Promise<void> {
  await client.delete(`/robots/${id}`)
}

/** PATCH /robots/{id}/status — update robot status */
export async function updateRobotStatus(id: string, status: string): Promise<Robot> {
  const response = await client.patch<Robot>(`/robots/${id}/status`, { status })
  return response.data
}

/** GET /robots/{id}/capabilities — fetch capabilities bound to a robot */
export async function getRobotCapabilities(id: string): Promise<string[]> {
  const response = await client.get<string[]>(`/robots/${id}/capabilities`)
  return response.data
}

/** PUT /robots/{id}/capabilities — update capabilities bound to a robot */
export async function updateRobotCapabilities(id: string, capabilities: string[]): Promise<void> {
  await client.put(`/robots/${id}/capabilities`, { capabilities })
}

/** GET /robot-models — list available robot models */
export async function listRobotModels(): Promise<RobotModel[]> {
  const response = await client.get<RobotModel[]>('/robot-models')
  return response.data
}

/** GET /robot-groups — list robot groups */
export async function listRobotGroups(params?: RobotListParams): Promise<PageResult<RobotGroup>> {
  const response = await client.get<PageResult<RobotGroup>>('/robot-groups', { params })
  return response.data
}
