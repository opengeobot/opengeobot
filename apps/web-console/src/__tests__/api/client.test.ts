import { describe, it, expect, vi, beforeEach } from 'vitest'

const instances: any[] = []

vi.doMock('axios', () => ({
  default: {
    create: vi.fn(() => {
      const fn = vi.fn() as any
      fn.get = vi.fn()
      fn.post = vi.fn()
      fn.put = vi.fn()
      fn.patch = vi.fn()
      fn.delete = vi.fn()
      fn.interceptors = {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      }
      instances.push(fn)
      return fn
    })
  }
}))

await import('@/api/client')
const axios = (await import('axios')).default as any

const clientInstance = instances[0]
const refreshInstance = instances[1]
const requestInterceptor = clientInstance.interceptors.request.use.mock.calls[0][0]
const responseErrorInterceptor = clientInstance.interceptors.response.use.mock.calls[0][1]

function mockLocation(pathname: string) {
  Object.defineProperty(window, 'location', {
    value: { pathname, href: '' },
    writable: true,
    configurable: true
  })
}

describe('HTTP client configuration', () => {
  it('creates axios instance with baseURL /api/v1', () => {
    expect(axios.create).toHaveBeenCalledWith(
      expect.objectContaining({ baseURL: '/api/v1' })
    )
  })

  it('creates refresh client with baseURL /api/v1', () => {
    expect(axios.create).toHaveBeenCalledTimes(2)
    expect(axios.create).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ baseURL: '/api/v1' })
    )
  })
})

describe('request interceptor', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('adds Bearer token from localStorage', () => {
    localStorage.setItem('token', 'my-token')
    const config = { headers: {} as Record<string, string> }
    const result = requestInterceptor(config)
    expect(result.headers.Authorization).toBe('Bearer my-token')
  })

  it('does not add Authorization header when no token', () => {
    const config = { headers: {} as Record<string, string> }
    const result = requestInterceptor(config)
    expect(result.headers.Authorization).toBeUndefined()
  })
})

describe('response interceptor - 401 token refresh', () => {
  beforeEach(() => {
    localStorage.clear()
    mockLocation('/dashboard')
    refreshInstance.post.mockReset()
    clientInstance.mockReset()
  })

  it('triggers token refresh on 401 and retries original request', async () => {
    localStorage.setItem('refresh_token', 'old-rt')

    const tokens = { access_token: 'new-at', refresh_token: 'new-rt', expires_in: 3600 }
    refreshInstance.post.mockResolvedValue({ data: tokens })
    clientInstance.mockResolvedValue({ data: 'retried' })

    const originalRequest = { headers: {} as Record<string, string>, url: '/users' }
    const error = {
      response: { status: 401, data: undefined },
      config: originalRequest,
      message: 'Unauthorized'
    }

    await responseErrorInterceptor(error)

    expect(refreshInstance.post).toHaveBeenCalledWith('/auth/refresh', {
      refresh_token: 'old-rt'
    })
    expect(localStorage.getItem('token')).toBe('new-at')
    expect(localStorage.getItem('refresh_token')).toBe('new-rt')
    expect(localStorage.getItem('expires_at')).toBeTruthy()
    expect(clientInstance).toHaveBeenCalledWith(originalRequest)
    expect(originalRequest.headers.Authorization).toBe('Bearer new-at')
  })

  it('clears auth storage and redirects when no refresh token on 401', async () => {
    localStorage.setItem('token', 'expired')
    localStorage.setItem('user', 'data')

    const originalRequest = { headers: {} as Record<string, string>, url: '/users' }
    const error = {
      response: { status: 401, data: undefined },
      config: originalRequest,
      message: 'Unauthorized'
    }

    await expect(responseErrorInterceptor(error)).rejects.toMatchObject({
      code: expect.any(String)
    })

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refresh_token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
    expect(window.location.href).toBe('/login')
  })

  it('clears auth storage when token refresh fails', async () => {
    localStorage.setItem('token', 'expired')
    localStorage.setItem('refresh_token', 'bad-rt')
    localStorage.setItem('user', 'data')

    refreshInstance.post.mockRejectedValue({
      response: { status: 401, data: undefined },
      message: 'Refresh failed'
    })

    const originalRequest = { headers: {} as Record<string, string>, url: '/users' }
    const error = {
      response: { status: 401, data: undefined },
      config: originalRequest,
      message: 'Unauthorized'
    }

    await expect(responseErrorInterceptor(error)).rejects.toMatchObject({
      code: expect.any(String)
    })

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refresh_token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
  })

  it('passes through non-401 errors as normalized problem', async () => {
    const error = {
      response: { status: 500, data: undefined },
      config: { url: '/users' },
      message: 'Server Error'
    }

    await expect(responseErrorInterceptor(error)).rejects.toMatchObject({
      status: 500,
      code: 'unknown'
    })
  })

  it('normalizes network errors without response', async () => {
    const error = {
      message: 'Network Error'
    }

    await expect(responseErrorInterceptor(error)).rejects.toMatchObject({
      status: 0,
      code: 'network_error'
    })
  })
})
