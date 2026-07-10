import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const mockStorage: Record<string, string> = {}
const localStorageMock = {
  getItem: vi.fn((key: string) => mockStorage[key] ?? null),
  setItem: vi.fn((key: string, value: string) => { mockStorage[key] = value }),
  removeItem: vi.fn((key: string) => { delete mockStorage[key] }),
  clear: vi.fn(() => { Object.keys(mockStorage).forEach(k => delete mockStorage[k]) })
}

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

import { usePlatformStore } from '@/stores/platform'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  Object.keys(mockStorage).forEach(k => delete mockStorage[k])
})

describe('platform store', () => {
  it('initial isOnline from navigator.onLine', () => {
    const store = usePlatformStore()
    expect(store.isOnline).toBe(navigator.onLine)
  })

  it('setLocale updates currentLocale', () => {
    const store = usePlatformStore()
    store.setLocale('en-US')
    expect(store.currentLocale).toBe('en-US')
  })

  it('switchOrg updates currentOrg', () => {
    const store = usePlatformStore()
    store.switchOrg('org-1')
    expect(store.currentOrg).toBe('org-1')
  })

  it('switchOrg persists to localStorage', () => {
    const store = usePlatformStore()
    store.switchOrg('org-2')
    expect(localStorageMock.setItem).toHaveBeenCalledWith('current_org', 'org-2')
  })

  it('currentOrg is null when localStorage empty', () => {
    const store = usePlatformStore()
    expect(store.currentOrg).toBeNull()
  })

  it('setOnline updates isOnline', () => {
    const store = usePlatformStore()
    store.setOnline(false)
    expect(store.isOnline).toBe(false)
    store.setOnline(true)
    expect(store.isOnline).toBe(true)
  })

  it('online event sets isOnline to true', () => {
    const store = usePlatformStore()
    store.setOnline(false)
    window.dispatchEvent(new Event('online'))
    expect(store.isOnline).toBe(true)
  })

  it('offline event sets isOnline to false', () => {
    const store = usePlatformStore()
    store.setOnline(true)
    window.dispatchEvent(new Event('offline'))
    expect(store.isOnline).toBe(false)
  })
})
