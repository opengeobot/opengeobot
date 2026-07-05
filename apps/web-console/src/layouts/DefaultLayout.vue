<script setup lang="ts">
// Function: Default application layout with top bar, sidebar, breadcrumb and user menu
// Time: 2026-07-04
// Author: AxeXie
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { usePlatformStore } from '@/stores/platform'
import AppOfflineIndicator from '@/components/AppOfflineIndicator.vue'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const platformStore = usePlatformStore()

const envLabel = import.meta.env.MODE
const showUserMenu = ref(false)
const sidebarCollapsed = ref(false)

interface NavItem {
  key: string
  path: string
  permission?: string
  children?: NavItem[]
}

const navItems: NavItem[] = [
  { key: 'nav.dashboard', path: '/dashboard' },
  { key: 'nav.robots', path: '/robots' },
  { key: 'nav.missions', path: '/missions' },
  { key: 'nav.skills', path: '/skills' },
  { key: 'nav.policies', path: '/policies' },
  { key: 'nav.maps', path: '/maps' },
  { key: 'nav.monitor', path: '/monitor' },
  { key: 'nav.safety', path: '/safety' },
  { key: 'nav.media', path: '/media' },
  {
    key: 'nav.system',
    path: '/system',
    children: [
      { key: 'nav.users', path: '/system/users', permission: 'platform.user.read' },
      { key: 'nav.orgs', path: '/system/orgs', permission: 'platform.org.manage' },
      { key: 'nav.roles', path: '/system/roles', permission: 'platform.role.read' },
      { key: 'nav.permissions', path: '/system/permissions', permission: 'platform.permission.read' },
      { key: 'nav.dict', path: '/system/dict', permission: 'platform.dictionary.read' },
      { key: 'nav.i18n', path: '/system/i18n', permission: 'platform.i18n.read' },
      { key: 'nav.config', path: '/system/config', permission: 'platform.config.read' },
      { key: 'nav.audit', path: '/system/audit', permission: 'audit.audit.read' }
    ]
  }
]

function hasPermission(item: NavItem): boolean {
  if (!item.permission) return true
  return authStore.permissions.includes(item.permission)
}

function visibleChildren(item: NavItem): NavItem[] {
  return (item.children ?? []).filter(hasPermission)
}

const languages = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' }
]

interface Breadcrumb {
  title: string
  path: string
}

const breadcrumbs = computed<Breadcrumb[]>(() => {
  const crumbs: Breadcrumb[] = []
  for (const record of route.matched) {
    if (record.meta.titleKey) {
      crumbs.push({
        title: t(record.meta.titleKey),
        path: record.path
      })
    }
  }
  return crumbs
})

function changeLanguage(value: string): void {
  locale.value = value
  platformStore.setLocale(value)
}

async function handleLogout(): Promise<void> {
  await authStore.logout()
  showUserMenu.value = false
  router.push('/login')
}

function toggleUserMenu(): void {
  showUserMenu.value = !showUserMenu.value
}

function goToProfile(): void {
  showUserMenu.value = false
  router.push('/profile')
}

function toggleSidebar(): void {
  sidebarCollapsed.value = !sidebarCollapsed.value
}
</script>

<template>
  <div class="layout">
    <header class="top-bar">
      <div class="top-bar-left">
        <button class="sidebar-toggle" :title="sidebarCollapsed ? t('common.expand') : t('common.collapse')" @click="toggleSidebar">
          <span class="toggle-icon">☰</span>
        </button>
        <span class="logo">{{ t('app.title') }}</span>
        <span class="env-badge" :class="envLabel">{{ envLabel }}</span>
      </div>
      <div class="top-bar-right">
        <AppOfflineIndicator v-if="!platformStore.isOnline" />
        <button class="bell-btn" :title="t('common.notifications')">
          <span class="bell-icon">🔔</span>
          <span class="bell-dot" />
        </button>
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
            <button class="dropdown-item" @click="goToProfile">
              {{ t('nav.profile') }}
            </button>
            <button class="dropdown-item" @click="handleLogout">
              {{ t('common.logout') }}
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="layout-body">
      <aside class="sidebar" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
        <nav class="nav">
          <template v-for="item in navItems" :key="item.path">
            <RouterLink
              :to="item.path"
              class="nav-item"
              active-class="nav-item-active"
              :title="t(item.key)"
            >
              <span class="nav-text">{{ t(item.key) }}</span>
            </RouterLink>
            <template v-if="item.children && visibleChildren(item).length > 0">
              <RouterLink
                v-for="child in visibleChildren(item)"
                :key="child.path"
                :to="child.path"
                class="nav-item nav-item-child"
                active-class="nav-item-active"
                :title="t(child.key)"
              >
                <span class="nav-text">{{ t(child.key) }}</span>
              </RouterLink>
            </template>
          </template>
        </nav>
      </aside>

      <main class="main-content">
        <nav v-if="breadcrumbs.length > 0" class="breadcrumb-bar">
          <template v-for="(crumb, index) in breadcrumbs" :key="crumb.path">
            <span v-if="index > 0" class="breadcrumb-sep">/</span>
            <span
              class="breadcrumb-item"
              :class="{ 'breadcrumb-active': index === breadcrumbs.length - 1 }"
            >
              {{ crumb.title }}
            </span>
          </template>
        </nav>

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

.sidebar-toggle {
  border: none;
  background: transparent;
  color: #f1f5f9;
  font-size: 1.125rem;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
}

.sidebar-toggle:hover {
  background-color: #334155;
}

.toggle-icon {
  display: inline-block;
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

.bell-btn {
  position: relative;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0.25rem;
  font-size: 1rem;
}

.bell-icon {
  display: inline-block;
}

.bell-dot {
  position: absolute;
  top: 0.125rem;
  right: 0.125rem;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background-color: #ef4444;
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
  overflow-x: hidden;
  overflow-y: auto;
  transition: width 0.2s;
}

.sidebar-collapsed {
  width: 3rem;
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
  white-space: nowrap;
  overflow: hidden;
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

.nav-item-child {
  padding-left: 2rem;
  font-size: 0.8125rem;
}

.sidebar-collapsed .nav-text {
  display: none;
}

.main-content {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
  background-color: #ffffff;
}

.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
  font-size: 0.8125rem;
}

.breadcrumb-item {
  color: #64748b;
}

.breadcrumb-active {
  color: #1e293b;
  font-weight: 600;
}

.breadcrumb-sep {
  color: #cbd5e1;
}
</style>
