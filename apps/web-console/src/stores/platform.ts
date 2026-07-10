// Function: Platform global state store
// Time: 2026-07-03
// Author: AxeXie
import { defineStore } from 'pinia'
import { ref, onScopeDispose } from 'vue'

const ORG_STORAGE_KEY = 'current_org'

export const usePlatformStore = defineStore('platform', () => {
  const currentLocale = ref<string>('zh-CN')
  const currentOrg = ref<string | null>(localStorage.getItem(ORG_STORAGE_KEY))
  const isOnline = ref<boolean>(navigator.onLine)

  function setLocale(locale: string) {
    currentLocale.value = locale
  }

  function setOnline(online: boolean) {
    isOnline.value = online
  }

  function switchOrg(orgId: string) {
    currentOrg.value = orgId
    localStorage.setItem(ORG_STORAGE_KEY, orgId)
  }

  function handleOnline() {
    isOnline.value = true
  }

  function handleOffline() {
    isOnline.value = false
  }

  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)

  onScopeDispose(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return { currentLocale, currentOrg, isOnline, setLocale, setOnline, switchOrg }
})
