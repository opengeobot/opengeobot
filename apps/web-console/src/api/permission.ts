// Function: Permission query API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type { Permission, PermissionGroup } from '@/types/api'

/** GET /permissions — list all permissions */
export async function listPermissions(): Promise<Permission[]> {
  const response = await client.get<Permission[]>('/permissions')
  return response.data
}

/** GET /permissions/grouped — list permissions grouped by module */
export async function listPermissionsByModule(): Promise<PermissionGroup[]> {
  const response = await client.get<PermissionGroup[]>('/permissions/grouped')
  return response.data
}

/** GET /permissions?role_id={id} — get permissions assigned to a role */
export async function getPermissionsByRole(roleId: string): Promise<Permission[]> {
  const response = await client.get<Permission[]>('/permissions', {
    params: { role_id: roleId }
  })
  return response.data
}
