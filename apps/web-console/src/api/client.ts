// Function: Axios HTTP client with auth and error interceptors
// Time: 2026-07-03
// Author: AxeXie
import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type { ProblemDetails } from '@/types/api'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

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
  (error: AxiosError<ProblemDetails>) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('token')
      }
      const problem: ProblemDetails = data ?? {
        type: 'about:blank',
        title: error.message,
        status,
        code: 'unknown',
        message_key: 'common.error',
        arguments: {},
        trace_id: '',
        instance: error.response.config?.url ?? ''
      }
      return Promise.reject(problem)
    }
    return Promise.reject(error)
  }
)

export default client
