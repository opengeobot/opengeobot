// Function: Platform global state store
// Time: 2026-07-03
// Author: AxeXie
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePlatformStore = defineStore('platform', () => {
  const currentLocale = ref<string>('zh-CN')
  const currentOrg = ref<string | null>(null)
  const isOnline = ref<boolean>(true)

  function setLocale(locale: string) {
    currentLocale.value = locale
  }

  function setOnline(online: boolean) {
    isOnline.value = online
  }

  return { currentLocale, currentOrg, isOnline, setLocale, setOnline }
})
