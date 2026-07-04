<script setup lang="ts">
// Function: Default application layout with top bar and sidebar
// Time: 2026-07-03
// Author: AxeXie
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { usePlatformStore } from '@/stores/platform'
import AppOfflineIndicator from '@/components/AppOfflineIndicator.vue'

const { t, locale } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const platformStore = usePlatformStore()

const envLabel = import.meta.env.MODE
const showUserMenu = ref(false)

const navItems = [
  { key: 'nav.dashboard', path: '/dashboard' },
  { key: 'nav.devices', path: '/devices' },
  { key: 'nav.missions', path: '/missions' },
  { key: 'nav.capabilities', path: '/capabilities' },
  { key: 'nav.maps', path: '/maps' },
  { key: 'nav.monitor', path: '/monitor' },
  { key: 'nav.alarms', path: '/alarms' },
  { key: 'nav.memory', path: '/memory' },
  { key: 'nav.system', path: '/system' }
]

const languages = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' }
]

function changeLanguage(value: string) {
  locale.value = value
  platformStore.setLocale(value)
}

function handleLogout() {
  authStore.logout()
  showUserMenu.value = false
  router.push('/login')
}

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
}
</script>

<template>
  <div class="layout">
    <header class="top-bar">
      <div class="top-bar-left">
        <span class="logo">{{ t('app.title') }}</span>
        <span class="env-badge" :class="envLabel">{{ envLabel }}</span>
      </div>
      <div class="top-bar-right">
        <AppOfflineIndicator v-if="!platformStore.isOnline" />
        <select
          class="lang-select"
          :value="locale"
          @change="changeLanguage(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="lang in languages" :key="lang.value" :value="lang.value">
            {{ lang.label }}
          </option>
        </select>
        <div class="user-menu">
          <button class="user-btn" @click="toggleUserMenu">
            {{ authStore.user?.username ?? t('common.username') }}
          </button>
          <div v-if="showUserMenu" class="user-dropdown">
            <button class="dropdown-item" @click="handleLogout">
              {{ t('common.logout') }}
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="layout-body">
      <aside class="sidebar">
        <nav class="nav">
          <RouterLink
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            active-class="nav-item-active"
          >
            {{ t(item.key) }}
          </RouterLink>
        </nav>
      </aside>

      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 3.5rem;
  padding: 0 1rem;
  background-color: #1e293b;
  color: #f1f5f9;
  flex-shrink: 0;
}

.top-bar-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.logo {
  font-size: 1rem;
  font-weight: 600;
}

.env-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  background-color: #475569;
  color: #e2e8f0;
}

.env-badge.production {
  background-color: #dc2626;
}

.env-badge.development {
  background-color: #16a34a;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.lang-select {
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  border: 1px solid #475569;
  background-color: #334155;
  color: #f1f5f9;
  font-size: 0.875rem;
  cursor: pointer;
}

.user-menu {
  position: relative;
}

.user-btn {
  padding: 0.375rem 0.75rem;
  border: none;
  background: transparent;
  color: #f1f5f9;
  font-size: 0.875rem;
  cursor: pointer;
}

.user-btn:hover {
  text-decoration: underline;
}

.user-dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  background-color: #1e293b;
  border: 1px solid #475569;
  border-radius: 0.25rem;
  min-width: 8rem;
  z-index: 10;
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: none;
  background: transparent;
  color: #f1f5f9;
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;
}

.dropdown-item:hover {
  background-color: #334155;
}

.layout-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 14rem;
  background-color: #f8fafc;
  border-right: 1px solid #e2e8f0;
  flex-shrink: 0;
  overflow-y: auto;
}

.nav {
  display: flex;
  flex-direction: column;
  padding: 0.5rem 0;
}

.nav-item {
  display: block;
  padding: 0.625rem 1rem;
  color: #475569;
  text-decoration: none;
  font-size: 0.875rem;
  transition: background-color 0.15s;
}

.nav-item:hover {
  background-color: #e2e8f0;
}

.nav-item-active {
  background-color: #dbeafe;
  color: #1d4ed8;
  font-weight: 600;
  border-left: 3px solid #2563eb;
}

.main-content {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
  background-color: #ffffff;
}
</style>
