// Function: User management API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type {
  User,
  CreateUserRequest,
  UpdateUserRequest,
  UserListParams,
  Role,
  PageResult
} from '@/types/api'

/** GET /users — paginated user list with filters */
export async function listUsers(params: UserListParams): Promise<PageResult<User>> {
  const response = await client.get<PageResult<User>>('/users', { params })
  return response.data
}

/** POST /users — create a new user */
export async function createUser(data: CreateUserRequest): Promise<User> {
  const response = await client.post<User>('/users', data)
  return response.data
}

/** GET /users/{id} — fetch a single user */
export async function getUser(id: string): Promise<User> {
  const response = await client.get<User>(`/users/${id}`)
  return response.data
}

/** PUT /users/{id} — update an existing user */
export async function updateUser(id: string, data: UpdateUserRequest): Promise<User> {
  const response = await client.put<User>(`/users/${id}`, data)
  return response.data
}

/** PATCH /users/{id}/status — enable or disable a user */
export async function updateUserStatus(id: string, status: string): Promise<User> {
  const response = await client.patch<User>(`/users/${id}/status`, { status })
  return response.data
}

/** GET /users/{id}/roles — fetch roles assigned to a user */
export async function getUserRoles(id: string): Promise<Role[]> {
  const response = await client.get<Role[]>(`/users/${id}/roles`)
  return response.data
}

/** PUT /users/{id}/roles — assign roles to a user */
export async function assignRoles(id: string, roleIds: string[]): Promise<void> {
  await client.put(`/users/${id}/roles`, { role_ids: roleIds })
}
