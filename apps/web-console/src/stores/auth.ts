// Function: Authentication store
// Time: 2026-07-03
// Author: AxeXie
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<{ id: string; username: string } | null>(null)

  const isAuthenticated = computed(() => !!token.value)

  function setAuth(newToken: string, newUser: { id: string; username: string }) {
    token.value = newToken
    user.value = newUser
    localStorage.setItem('token', newToken)
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
  }

  function restore() {
    const saved = localStorage.getItem('token')
    if (saved) token.value = saved
  }

  return { token, user, isAuthenticated, setAuth, logout, restore }
})
