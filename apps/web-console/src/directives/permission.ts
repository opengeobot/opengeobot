// Function: v-permission directive for element-level permission control
// Time: 2026-07-04
// Author: AxeXie
import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

function checkPermission(required: string | string[]): boolean {
  const authStore = useAuthStore()
  const permissions = authStore.permissions
  if (Array.isArray(required)) {
    return required.some((p: string) => permissions.includes(p))
  }
  return permissions.includes(required)
}

export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  beforeMount(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    if (!binding.value) return
    if (!checkPermission(binding.value)) {
      el.style.display = 'none'
    }
  }
}
