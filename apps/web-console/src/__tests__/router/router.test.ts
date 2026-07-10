import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockAuthStore = {
  token: null as string | null,
  user: null as { permissions: string[] } | null,
  isTokenExpired: vi.fn(() => true),
  restore: vi.fn(),
  permissions: [] as string[]
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockAuthStore
}))

vi.mock('@/layouts/DefaultLayout.vue', () => ({ default: { template: '<div><router-view/></div>' } }))

import { createRouter, createMemoryHistory, type RouteRecordRaw } from 'vue-router'
import type { NavigationGuardWithThis } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: { template: '<div>login</div>' }
  },
  {
    path: '/',
    component: { template: '<div><router-view/></div>' },
    meta: { requiresAuth: true },
    children: [
      { path: 'dashboard', name: 'dashboard', component: { template: '<div>dashboard</div>' }, meta: { requiresAuth: true } },
      { path: 'robots', name: 'robots', component: { template: '<div>robots</div>' }, meta: { requiresAuth: true, permission: 'robot.read' } },
      { path: 'missions', name: 'missions', component: { template: '<div>missions</div>' }, meta: { requiresAuth: true, permission: 'mission.read' } }
    ]
  }
]

function createTestRouter() {
  const router = createRouter({ history: createMemoryHistory(), routes })

  let restored = false
  const guard: NavigationGuardWithThis<undefined> = (to, _from, next) => {
    if (!restored) {
      mockAuthStore.restore()
      restored = true
    }
    if (to.meta.requiresAuth) {
      if (!mockAuthStore.token || mockAuthStore.isTokenExpired()) {
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }
    }
    if (to.meta.permission && mockAuthStore.user) {
      const required = to.meta.permission as string
      if (!mockAuthStore.permissions.includes(required)) {
        next('/dashboard')
        return
      }
    }
    next()
  }
  router.beforeEach(guard)
  return router
}

beforeEach(() => {
  vi.clearAllMocks()
  mockAuthStore.token = null
  mockAuthStore.user = null
  mockAuthStore.permissions = []
  mockAuthStore.isTokenExpired = vi.fn(() => true)
})

describe('router guard', () => {
  it('unauthenticated user redirected to login', async () => {
    const router = createTestRouter()
    mockAuthStore.token = null
    mockAuthStore.isTokenExpired = vi.fn(() => true)

    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('authenticated user with valid token passes', async () => {
    const router = createTestRouter()
    mockAuthStore.token = 'valid'
    mockAuthStore.isTokenExpired = vi.fn(() => false)

    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('expired token redirects to login', async () => {
    const router = createTestRouter()
    mockAuthStore.token = 'expired'
    mockAuthStore.isTokenExpired = vi.fn(() => true)

    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('user without permission redirected to dashboard', async () => {
    const router = createTestRouter()
    mockAuthStore.token = 'valid'
    mockAuthStore.isTokenExpired = vi.fn(() => false)
    mockAuthStore.user = { permissions: [] }
    mockAuthStore.permissions = []

    await router.push('/robots')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('user with correct permission passes', async () => {
    const router = createTestRouter()
    mockAuthStore.token = 'valid'
    mockAuthStore.isTokenExpired = vi.fn(() => false)
    mockAuthStore.user = { permissions: ['robot.read'] }
    mockAuthStore.permissions = ['robot.read']

    await router.push('/robots')
    expect(router.currentRoute.value.name).toBe('robots')
  })

  it('non-protected route passes without auth check', async () => {
    const router = createTestRouter()
    mockAuthStore.token = null
    mockAuthStore.isTokenExpired = vi.fn(() => true)

    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('restore is called on first navigation', async () => {
    const router = createTestRouter()
    await router.push('/login')
    expect(mockAuthStore.restore).toHaveBeenCalledTimes(1)
  })
})
