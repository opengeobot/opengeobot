// Function: Role management API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type {
  Role,
  CreateRoleRequest,
  UpdateRoleRequest,
  Permission,
  FilterParams,
  PageResult
} from '@/types/api'

/** GET /roles — paginated role list */
export async function listRoles(params: FilterParams): Promise<PageResult<Role>> {
  const response = await client.get<PageResult<Role>>('/roles', { params })
  return response.data
}

/** POST /roles — create a new role */
export async function createRole(data: CreateRoleRequest): Promise<Role> {
  const response = await client.post<Role>('/roles', data)
  return response.data
}

/** GET /roles/{id} — fetch a single role */
export async function getRole(id: string): Promise<Role> {
  const response = await client.get<Role>(`/roles/${id}`)
  return response.data
}

/** PUT /roles/{id} — update an existing role */
export async function updateRole(id: string, data: UpdateRoleRequest): Promise<Role> {
  const response = await client.put<Role>(`/roles/${id}`, data)
  return response.data
}

/** GET /roles/{id}/permissions — fetch permissions assigned to a role */
export async function getRolePermissions(id: string): Promise<Permission[]> {
  const response = await client.get<Permission[]>(`/roles/${id}/permissions`)
  return response.data
}

/** PUT /roles/{id}/permissions — assign permissions to a role */
export async function assignPermissions(id: string, permissionCodes: string[]): Promise<void> {
  await client.put(`/roles/${id}/permissions`, { permission_codes: permissionCodes })
}
