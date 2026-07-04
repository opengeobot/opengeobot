<script setup lang="ts">
// Function: Offline detection indicator
// Time: 2026-07-03
// Author: AxeXie
import { ref, onMounted, onUnmounted } from 'vue'
import { usePlatformStore } from '@/stores/platform'
import { useI18n } from 'vue-i18n'

const platformStore = usePlatformStore()
const { t } = useI18n()

const isOnline = ref<boolean>(navigator.onLine)

function handleOnline() {
  isOnline.value = true
  platformStore.setOnline(true)
}

function handleOffline() {
  isOnline.value = false
  platformStore.setOnline(false)
}

onMounted(() => {
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  platformStore.setOnline(isOnline.value)
})

onUnmounted(() => {
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
})
</script>

<template>
  <div v-if="!isOnline" class="offline-indicator">
    <span class="offline-dot" />
    <span>{{ t('common.offline') }}</span>
  </div>
</template>

<style scoped>
.offline-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background-color: #ef4444;
  color: #fff;
  font-size: 0.875rem;
  font-weight: 500;
}

.offline-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background-color: #fff;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
