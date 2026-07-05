// Function: Axios HTTP client with auth and error interceptors
// Time: 2026-07-04
// Author: AxeXie
import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type { ProblemDetails, TokenResponse } from '@/types/api'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

/** Separate instance for token refresh — bypasses interceptors to avoid loops */
const refreshClient = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: unknown) => void
}> = []

function processQueue(token: string | null, error: unknown | null): void {
  failedQueue.forEach(({ resolve, reject }) => {
    if (token !== null) {
      resolve(token)
    } else {
      reject(error)
    }
  })
  failedQueue = []
}

function clearAuthStorage(): void {
  localStorage.removeItem('token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('expires_at')
  localStorage.removeItem('user')
}

function redirectToLogin(): void {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

function normalizeProblem(error: AxiosError<ProblemDetails>): ProblemDetails {
  if (error.response) {
    const { status, data } = error.response
    return data ?? {
      type: 'about:blank',
      title: error.message,
      status,
      code: 'unknown',
      message_key: 'common.error',
      arguments: {},
      trace_id: '',
      instance: error.config?.url ?? ''
    }
  }
  return {
    type: 'about:blank',
    title: error.message,
    status: 0,
    code: 'network_error',
    message_key: 'common.error',
    arguments: {},
    trace_id: '',
    instance: ''
  }
}

client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error)
)

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetails>) => {
    const originalRequest = error.config as RetryableConfig | undefined

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      const refreshTokenValue = localStorage.getItem('refresh_token')

      if (!refreshTokenValue) {
        clearAuthStorage()
        redirectToLogin()
        return Promise.reject(normalizeProblem(error))
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: (token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(client(originalRequest))
            },
            reject: (err: unknown) => reject(err)
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const response = await refreshClient.post<TokenResponse>('/auth/refresh', {
          refresh_token: refreshTokenValue
        })
        const tokens = response.data
        localStorage.setItem('token', tokens.access_token)
        localStorage.setItem('refresh_token', tokens.refresh_token)
        localStorage.setItem('expires_at', String(Date.now() + tokens.expires_in * 1000))

        processQueue(tokens.access_token, null)

        originalRequest.headers.Authorization = `Bearer ${tokens.access_token}`
        return client(originalRequest)
      } catch (refreshError) {
        processQueue(null, refreshError)
        clearAuthStorage()
        redirectToLogin()
        return Promise.reject(normalizeProblem(refreshError as AxiosError<ProblemDetails>))
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(normalizeProblem(error))
  }
)

export default client
