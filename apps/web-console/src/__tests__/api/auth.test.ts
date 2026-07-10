import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/client', () => ({ default: mockClient }))

import {
  loginApi,
  refreshTokenApi,
  logoutApi,
  getProfile,
  updateProfileApi
} from '@/api/auth'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('auth API', () => {
  it('loginApi posts credentials to /auth/login', async () => {
    const tokens = { access_token: 'at', refresh_token: 'rt', expires_in: 3600 }
    mockClient.post.mockResolvedValue({ data: tokens })

    const result = await loginApi('admin', 'pass123')

    expect(mockClient.post).toHaveBeenCalledWith('/auth/login', {
      username: 'admin',
      password: 'pass123'
    })
    expect(result).toEqual(tokens)
  })

  it('refreshTokenApi posts refresh token to /auth/refresh', async () => {
    const tokens = { access_token: 'at2', refresh_token: 'rt2', expires_in: 3600 }
    mockClient.post.mockResolvedValue({ data: tokens })

    const result = await refreshTokenApi('old-rt')

    expect(mockClient.post).toHaveBeenCalledWith('/auth/refresh', {
      refresh_token: 'old-rt'
    })
    expect(result).toEqual(tokens)
  })

  it('logoutApi posts to /auth/logout', async () => {
    mockClient.post.mockResolvedValue({})

    await logoutApi()

    expect(mockClient.post).toHaveBeenCalledWith('/auth/logout')
  })

  it('getProfile gets from /profile', async () => {
    const profile = {
      user_id: 'u1',
      username: 'admin',
      display_name: 'Admin',
      email: null,
      phone: null,
      avatar: null,
      status: 'active',
      permissions: ['user:read']
    }
    mockClient.get.mockResolvedValue({ data: profile })

    const result = await getProfile()

    expect(mockClient.get).toHaveBeenCalledWith('/profile')
    expect(result).toEqual(profile)
  })

  it('updateProfileApi puts to /profile', async () => {
    const profile = {
      user_id: 'u1',
      username: 'admin',
      display_name: 'New Name',
      email: 'new@test.com',
      phone: null,
      avatar: null,
      status: 'active',
      permissions: []
    }
    mockClient.put.mockResolvedValue({ data: profile })

    const result = await updateProfileApi({ display_name: 'New Name', email: 'new@test.com' })

    expect(mockClient.put).toHaveBeenCalledWith('/profile', {
      display_name: 'New Name',
      email: 'new@test.com'
    })
    expect(result).toEqual(profile)
  })
})
