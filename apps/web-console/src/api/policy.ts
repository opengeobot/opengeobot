// Function: Policy management API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  Policy,
  PolicyVersion,
  CreatePolicyRequest,
  UpdatePolicyRequest,
  PolicyListParams,
  PageResult
} from '@/types/api'

/** GET /policies — paginated policy list */
export async function listPolicies(params: PolicyListParams): Promise<PageResult<Policy>> {
  const response = await client.get<PageResult<Policy>>('/policies', { params })
  return response.data
}

/** GET /policies/{id} — fetch a single policy */
export async function getPolicy(id: string): Promise<Policy> {
  const response = await client.get<Policy>(`/policies/${id}`)
  return response.data
}

/** POST /policies — create a new policy */
export async function createPolicy(data: CreatePolicyRequest): Promise<Policy> {
  const response = await client.post<Policy>('/policies', data)
  return response.data
}

/** PUT /policies/{id} — update a policy */
export async function updatePolicy(id: string, data: UpdatePolicyRequest): Promise<Policy> {
  const response = await client.put<Policy>(`/policies/${id}`, data)
  return response.data
}

/** POST /policies/{id}/publish — publish a policy (generates a new version) */
export async function publishPolicy(id: string): Promise<Policy> {
  const response = await client.post<Policy>(`/policies/${id}/publish`)
  return response.data
}

/** GET /policies/{id}/versions — fetch policy version history */
export async function listPolicyVersions(id: string): Promise<PolicyVersion[]> {
  const response = await client.get<PolicyVersion[]>(`/policies/${id}/versions`)
  return response.data
}
