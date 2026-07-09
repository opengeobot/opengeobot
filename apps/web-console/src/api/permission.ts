// Function: Permission query API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type { Permission, PermissionGroup } from '@/types/api'

/** GET /permissions - list all permissions */
export async function listPermissions(): Promise<Permission[]> {
  const response = await client.get<Permission[]>('/permissions')
  return response.data
}

/** GET /permissions - list permissions grouped by module (grouped in frontend) */
export async function listPermissionsByModule(): Promise<PermissionGroup[]> {
  const response = await client.get<Permission[]>('/permissions')
  const grouped: Record<string, Permission[]> = {}
  for (const perm of response.data) {
    if (!grouped[perm.module]) grouped[perm.module] = []
    grouped[perm.module].push(perm)
  }
  return Object.entries(grouped).map(([module, permissions]) => ({ module, permissions }))
}

/** GET /permissions/roles/{roleId} - get permissions assigned to a role */
export async function getPermissionsByRole(roleId: string): Promise<Permission[]> {
  const response = await client.get<Permission[]>(`/permissions/roles/${roleId}`)
  return response.data
}
