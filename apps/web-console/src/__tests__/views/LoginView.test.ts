import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    te: () => true,
    locale: { value: 'zh-CN' }
  })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} })
}))

const mockLogin = vi.fn()
const mockClearAuth = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: mockLogin,
    clearAuth: mockClearAuth,
    token: null,
    user: null,
    permissions: [],
    isAuthenticated: false,
    isTokenExpired: () => true
  })
}))

vi.mock('@/stores/platform', () => ({
  usePlatformStore: () => ({
    currentLocale: 'zh-CN',
    setLocale: vi.fn()
  })
}))

import LoginView from '@/views/LoginView.vue'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('LoginView', () => {
  it('renders username and password inputs', () => {
    const wrapper = mount(LoginView)
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
  })

  it('renders submit button', () => {
    const wrapper = mount(LoginView)
    const btn = wrapper.find('button[type="submit"]')
    expect(btn.exists()).toBe(true)
  })

  it('submit button is disabled with empty fields', () => {
    const wrapper = mount(LoginView)
    const btn = wrapper.find('button[type="submit"]')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('submit calls login with form values', async () => {
    mockLogin.mockResolvedValue(undefined)
    const wrapper = mount(LoginView)

    await wrapper.find('input[type="text"]').setValue('admin')
    await wrapper.find('input[type="password"]').setValue('password123')
    await wrapper.find('form').trigger('submit.prevent')

    expect(mockClearAuth).toHaveBeenCalled()
    expect(mockLogin).toHaveBeenCalledWith('admin', 'password123')
  })

  it('failed login shows error message', async () => {
    mockLogin.mockRejectedValue({
      type: 'about:blank',
      title: 'Unauthorized',
      status: 401,
      code: 'AUTH_INVALID_CREDENTIALS',
      message_key: 'auth.login_failed',
      arguments: {},
      trace_id: 't1',
      instance: '/auth/login'
    })
    const wrapper = mount(LoginView)

    await wrapper.find('input[type="text"]').setValue('admin')
    await wrapper.find('input[type="password"]').setValue('wrongpass')
    await wrapper.find('form').trigger('submit.prevent')

    await vi.waitFor(() => {
      expect(wrapper.find('.form-error-block').exists()).toBe(true)
    })
  })
})
