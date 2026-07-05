// Function: Organization management API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type { Org, CreateOrgRequest, UpdateOrgRequest } from '@/types/api'

/** GET /orgs — fetch the org tree */
export async function listOrgs(): Promise<Org[]> {
  const response = await client.get<Org[]>('/orgs')
  return response.data
}

/** POST /orgs — create a new org node */
export async function createOrg(data: CreateOrgRequest): Promise<Org> {
  const response = await client.post<Org>('/orgs', data)
  return response.data
}

/** GET /orgs/{id} — fetch a single org node */
export async function getOrg(id: string): Promise<Org> {
  const response = await client.get<Org>(`/orgs/${id}`)
  return response.data
}

/** PUT /orgs/{id} — update an org node */
export async function updateOrg(id: string, data: UpdateOrgRequest): Promise<Org> {
  const response = await client.put<Org>(`/orgs/${id}`, data)
  return response.data
}

/** DELETE /orgs/{id} — delete an org node (must have no children) */
export async function deleteOrg(id: string): Promise<void> {
  await client.delete(`/orgs/${id}`)
}
