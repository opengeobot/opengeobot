// Function: Robot management API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  Robot,
  RobotModel,
  RobotGroup,
  RobotGroupMembers,
  RobotCapability,
  CreateRobotRequest,
  UpdateRobotRequest,
  CreateRobotModelRequest,
  UpdateRobotModelRequest,
  CreateRobotGroupRequest,
  UpdateRobotGroupRequest,
  RobotListParams,
  RobotModelListParams,
  RobotGroupListParams,
  ControlLease,
  AcquireControlLeaseRequest,
  PageResult
} from '@/types/api'

/** GET /robots — paginated robot list with filters */
export async function listRobots(params: RobotListParams): Promise<PageResult<Robot>> {
  const response = await client.get<PageResult<Robot>>('/robots', {
    params: {
      page: params.page_number,
      page_size: params.page_size,
      name: params.name,
      status: params.status,
      org_id: params.org_id,
      model_id: params.model_id
    }
  })
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
export async function getRobotCapabilities(id: string): Promise<RobotCapability[]> {
  const response = await client.get<{ robot_id: string; capabilities: RobotCapability[] }>(
    `/robots/${id}/capabilities`
  )
  return response.data.capabilities ?? []
}

/** PUT /robots/{id}/capabilities — update capabilities bound to a robot */
export async function updateRobotCapabilities(
  id: string,
  capabilities: RobotCapability[]
): Promise<void> {
  await client.put(`/robots/${id}/capabilities`, { capabilities })
}

/** GET /robot-models — paginated robot model list */
export async function listRobotModels(
  params?: RobotModelListParams
): Promise<PageResult<RobotModel>> {
  const response = await client.get<PageResult<RobotModel>>('/robot-models', { params })
  return response.data
}

/** GET /robot-models/{modelId} — fetch a single robot model */
export async function getRobotModel(modelId: string): Promise<RobotModel> {
  const response = await client.get<RobotModel>(`/robot-models/${modelId}`)
  return response.data
}

/** POST /robot-models — create a robot model */
export async function createRobotModel(data: CreateRobotModelRequest): Promise<RobotModel> {
  const response = await client.post<RobotModel>('/robot-models', data)
  return response.data
}

/** PUT /robot-models/{modelId} — update a robot model */
export async function updateRobotModel(
  modelId: string,
  data: UpdateRobotModelRequest
): Promise<RobotModel> {
  const response = await client.put<RobotModel>(`/robot-models/${modelId}`, data)
  return response.data
}

/** DELETE /robot-models/{modelId} — delete a robot model */
export async function deleteRobotModel(modelId: string): Promise<void> {
  await client.delete(`/robot-models/${modelId}`)
}

/** GET /robot-groups — paginated robot group list */
export async function listRobotGroups(
  params?: RobotGroupListParams
): Promise<PageResult<RobotGroup>> {
  const response = await client.get<PageResult<RobotGroup>>('/robot-groups', { params })
  return response.data
}

/** GET /robot-groups/{groupId} — fetch a single robot group */
export async function getRobotGroup(groupId: string): Promise<RobotGroup> {
  const response = await client.get<RobotGroup>(`/robot-groups/${groupId}`)
  return response.data
}

/** POST /robot-groups — create a robot group */
export async function createRobotGroup(data: CreateRobotGroupRequest): Promise<RobotGroup> {
  const response = await client.post<RobotGroup>('/robot-groups', data)
  return response.data
}

/** PUT /robot-groups/{groupId} — update a robot group */
export async function updateRobotGroup(
  groupId: string,
  data: UpdateRobotGroupRequest
): Promise<RobotGroup> {
  const response = await client.put<RobotGroup>(`/robot-groups/${groupId}`, data)
  return response.data
}

/** DELETE /robot-groups/{groupId} — delete a robot group */
export async function deleteRobotGroup(groupId: string): Promise<void> {
  await client.delete(`/robot-groups/${groupId}`)
}

/** GET /robot-groups/{groupId}/members — list group member robot IDs */
export async function listRobotGroupMembers(groupId: string): Promise<RobotGroupMembers> {
  const response = await client.get<RobotGroupMembers>(`/robot-groups/${groupId}/members`)
  return response.data
}

/** POST /robot-groups/{groupId}/members/{robotId} — add a robot to a group */
export async function addRobotGroupMember(groupId: string, robotId: string): Promise<void> {
  await client.post(`/robot-groups/${groupId}/members/${robotId}`)
}

/** DELETE /robot-groups/{groupId}/members/{robotId} — remove a robot from a group */
export async function removeRobotGroupMember(groupId: string, robotId: string): Promise<void> {
  await client.delete(`/robot-groups/${groupId}/members/${robotId}`)
}

/** GET /robots/{robotId}/control-leases — get active control lease (null if none) */
export async function getActiveControlLease(robotId: string): Promise<ControlLease | null> {
  const response = await client.get<ControlLease | ''>(`/robots/${robotId}/control-leases`)
  if (response.status === 204 || !response.data) {
    return null
  }
  return response.data as ControlLease
}

/** POST /robots/{robotId}/control-leases — acquire a control lease */
export async function acquireControlLease(
  robotId: string,
  data?: AcquireControlLeaseRequest
): Promise<ControlLease> {
  const response = await client.post<ControlLease>(
    `/robots/${robotId}/control-leases`,
    data ?? {}
  )
  return response.data
}

/** DELETE /robots/{robotId}/control-leases — release the active control lease */
export async function releaseControlLease(robotId: string): Promise<ControlLease> {
  const response = await client.delete<ControlLease>(`/robots/${robotId}/control-leases`)
  return response.data
}
