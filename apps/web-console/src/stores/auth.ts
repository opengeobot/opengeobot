// Function: Authentication store
// Time: 2026-07-04
// Author: AxeXie
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi, getProfile } from '@/api/auth'
import type { TokenResponse, UserProfile } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const expiresAt = ref<number | null>(null)
  const user = ref<UserProfile | null>(null)

  const isAuthenticated = computed<boolean>(() => !!token.value && !isTokenExpired())

  const permissions = computed<string[]>(() => user.value?.permissions ?? [])

  function isTokenExpired(): boolean {
    if (!expiresAt.value) return true
    return Date.now() >= expiresAt.value
  }

  function setTokens(response: TokenResponse): void {
    token.value = response.access_token
    refreshToken.value = response.refresh_token
    const expires = Date.now() + response.expires_in * 1000
    expiresAt.value = expires
    localStorage.setItem('token', response.access_token)
    localStorage.setItem('refresh_token', response.refresh_token)
    localStorage.setItem('expires_at', String(expires))
  }

  async function login(username: string, password: string): Promise<void> {
    const tokenResponse = await loginApi(username, password)
    setTokens(tokenResponse)
    try {
      const profile = await getProfile()
      user.value = profile
      localStorage.setItem('user', JSON.stringify(profile))
    } catch {
      // Tokens are valid but profile fetch failed; continue without user info
    }
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // Ignore logout API errors; clear local state regardless
    } finally {
      clearAuth()
    }
  }

  function clearAuth(): void {
    token.value = null
    refreshToken.value = null
    expiresAt.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('expires_at')
    localStorage.removeItem('user')
  }

  function restore(): void {
    const savedToken = localStorage.getItem('token')
    const savedRefresh = localStorage.getItem('refresh_token')
    const savedExpires = localStorage.getItem('expires_at')
    const savedUser = localStorage.getItem('user')
    if (savedToken) token.value = savedToken
    if (savedRefresh) refreshToken.value = savedRefresh
    if (savedExpires) expiresAt.value = Number(savedExpires)
    if (savedUser) {
      try {
        user.value = JSON.parse(savedUser) as UserProfile
      } catch {
        localStorage.removeItem('user')
      }
    }
  }

  return {
    token,
    refreshToken,
    expiresAt,
    user,
    permissions,
    isAuthenticated,
    isTokenExpired,
    setTokens,
    login,
    logout,
    clearAuth,
    restore
  }
})
