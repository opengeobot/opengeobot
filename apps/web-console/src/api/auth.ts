// Function: Auth and profile API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type { TokenResponse, UserProfile, UpdateProfileRequest } from '@/types/api'

/** POST /auth/login — exchange credentials for tokens */
export async function loginApi(username: string, password: string): Promise<TokenResponse> {
  const response = await client.post<TokenResponse>('/auth/login', { username, password })
  return response.data
}

/** POST /auth/refresh — exchange refresh_token for new tokens */
export async function refreshTokenApi(refreshToken: string): Promise<TokenResponse> {
  const response = await client.post<TokenResponse>('/auth/refresh', {
    refresh_token: refreshToken
  })
  return response.data
}

/** POST /auth/logout — invalidate current session */
export async function logoutApi(): Promise<void> {
  await client.post('/auth/logout')
}

/** GET /profile — fetch current user profile */
export async function getProfile(): Promise<UserProfile> {
  const response = await client.get<UserProfile>('/profile')
  return response.data
}

/** PUT /profile — update current user profile */
export async function updateProfileApi(data: UpdateProfileRequest): Promise<UserProfile> {
  const response = await client.put<UserProfile>('/profile', data)
  return response.data
}
