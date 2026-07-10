import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/auth', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  getProfile: vi.fn(),
  refreshTokenApi: vi.fn(),
  updateProfileApi: vi.fn()
}))

import { useAuthStore } from '@/stores/auth'
import { loginApi, logoutApi, getProfile } from '@/api/auth'

const mockStorage: Record<string, string> = {}
const localStorageMock = {
  getItem: vi.fn((key: string) => mockStorage[key] ?? null),
  setItem: vi.fn((key: string, value: string) => { mockStorage[key] = value }),
  removeItem: vi.fn((key: string) => { delete mockStorage[key] }),
  clear: vi.fn(() => { Object.keys(mockStorage).forEach(k => delete mockStorage[k]) })
}

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  Object.keys(mockStorage).forEach(k => delete mockStorage[k])
})

describe('auth store', () => {
  it('initial state has no token, no user, empty permissions', () => {
    const store = useAuthStore()
    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
    expect(store.permissions).toEqual([])
  })

  it('login sets token and user', async () => {
    const tokens = { access_token: 'at', refresh_token: 'rt', expires_in: 3600 }
    const profile = {
      user_id: '1', username: 'admin', display_name: 'Admin',
      email: null, phone: null, avatar: null, status: 'active', permissions: ['robot.read']
    }
    vi.mocked(loginApi).mockResolvedValue(tokens)
    vi.mocked(getProfile).mockResolvedValue(profile)

    const store = useAuthStore()
    await store.login('admin', 'pass')

    expect(store.token).toBe('at')
    expect(store.user).toEqual(profile)
    expect(store.permissions).toEqual(['robot.read'])
  })

  it('login stores token in localStorage', async () => {
    const tokens = { access_token: 'at', refresh_token: 'rt', expires_in: 3600 }
    vi.mocked(loginApi).mockResolvedValue(tokens)
    vi.mocked(getProfile).mockResolvedValue({
      user_id: '1', username: 'admin', display_name: 'Admin',
      email: null, phone: null, avatar: null, status: 'active', permissions: []
    })

    const store = useAuthStore()
    await store.login('admin', 'pass')

    expect(localStorageMock.setItem).toHaveBeenCalledWith('token', 'at')
    expect(localStorageMock.setItem).toHaveBeenCalledWith('refresh_token', 'rt')
  })

  it('logout clears token, user, permissions', async () => {
    vi.mocked(logoutApi).mockResolvedValue(undefined)

    const store = useAuthStore()
    store.setTokens({ access_token: 'at', refresh_token: 'rt', expires_in: 3600 })
    await store.login('admin', 'pass').catch(() => {})

    await store.logout()

    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
    expect(store.permissions).toEqual([])
  })

  it('logout removes token from localStorage', async () => {
    vi.mocked(logoutApi).mockResolvedValue(undefined)

    const store = useAuthStore()
    await store.logout()

    expect(localStorageMock.removeItem).toHaveBeenCalledWith('token')
    expect(localStorageMock.removeItem).toHaveBeenCalledWith('refresh_token')
    expect(localStorageMock.removeItem).toHaveBeenCalledWith('user')
  })

  it('restore loads token from localStorage', () => {
    mockStorage['token'] = 'saved-token'
    mockStorage['refresh_token'] = 'saved-rt'
    mockStorage['expires_at'] = String(Date.now() + 100000)
    mockStorage['user'] = JSON.stringify({
      user_id: '1', username: 'admin', display_name: 'Admin',
      email: null, phone: null, avatar: null, status: 'active', permissions: ['test.read']
    })

    const store = useAuthStore()
    store.restore()

    expect(store.token).toBe('saved-token')
    expect(store.user?.username).toBe('admin')
    expect(store.permissions).toEqual(['test.read'])
  })

  it('isTokenExpired returns true when no expiresAt', () => {
    const store = useAuthStore()
    expect(store.isTokenExpired()).toBe(true)
  })

  it('isTokenExpired returns true for expired token', () => {
    const store = useAuthStore()
    store.setTokens({ access_token: 'at', refresh_token: 'rt', expires_in: -1 })
    expect(store.isTokenExpired()).toBe(true)
  })

  it('isTokenExpired returns false for valid token', () => {
    const store = useAuthStore()
    store.setTokens({ access_token: 'at', refresh_token: 'rt', expires_in: 3600 })
    expect(store.isTokenExpired()).toBe(false)
  })

  it('isAuthenticated returns false without token', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
  })

  it('isAuthenticated returns true with valid token', () => {
    const store = useAuthStore()
    store.setTokens({ access_token: 'at', refresh_token: 'rt', expires_in: 3600 })
    expect(store.isAuthenticated).toBe(true)
  })
})
