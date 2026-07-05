// Function: Vue Router configuration
// Time: 2026-07-04
// Author: AxeXie
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    permission?: string
    titleKey?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/system',
    redirect: '/system/users'
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { titleKey: 'login.title' }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { requiresAuth: true, titleKey: 'nav.dashboard' }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true, permission: 'platform.profile.read', titleKey: 'nav.profile' }
  },
  {
    path: '/robots',
    name: 'robots',
    component: () => import('@/views/RobotManagementView.vue'),
    meta: { requiresAuth: true, permission: 'platform.robot.read', titleKey: 'nav.robots' }
  },
  {
    path: '/skills',
    name: 'skills',
    component: () => import('@/views/SkillManagementView.vue'),
    meta: { requiresAuth: true, permission: 'platform.skill.read', titleKey: 'nav.skills' }
  },
  {
    path: '/missions',
    name: 'missions',
    component: () => import('@/views/MissionManagementView.vue'),
    meta: { requiresAuth: true, permission: 'platform.mission.read', titleKey: 'nav.missions' }
  },
  {
    path: '/policies',
    name: 'policies',
    component: () => import('@/views/PolicyManagementView.vue'),
    meta: { requiresAuth: true, permission: 'platform.policy.read', titleKey: 'nav.policies' }
  },
  {
    path: '/safety',
    name: 'safety',
    component: () => import('@/views/SafetyControlView.vue'),
    meta: { requiresAuth: true, permission: 'platform.safety.read', titleKey: 'nav.safety' }
  },
  {
    path: '/maps',
    name: 'maps',
    component: () => import('@/views/MapManagementView.vue'),
    meta: { requiresAuth: true, permission: 'platform.map.read', titleKey: 'nav.maps' }
  },
  {
    path: '/monitor',
    name: 'monitor',
    component: () => import('@/views/MonitorView.vue'),
    meta: { requiresAuth: true, permission: 'platform.monitor.read', titleKey: 'nav.monitor' }
  },
  {
    path: '/media',
    name: 'media',
    component: () => import('@/views/MediaLibraryView.vue'),
    meta: { requiresAuth: true, permission: 'platform.media.read', titleKey: 'nav.media' }
  },
  {
    path: '/system/users',
    name: 'system-users',
    component: () => import('@/views/system/UserManagement.vue'),
    meta: { requiresAuth: true, permission: 'platform.user.read', titleKey: 'nav.users' }
  },
  {
    path: '/system/orgs',
    name: 'system-orgs',
    component: () => import('@/views/system/OrgManagement.vue'),
    meta: { requiresAuth: true, permission: 'platform.org.manage', titleKey: 'nav.orgs' }
  },
  {
    path: '/system/roles',
    name: 'system-roles',
    component: () => import('@/views/system/RoleManagement.vue'),
    meta: { requiresAuth: true, permission: 'platform.role.read', titleKey: 'nav.roles' }
  },
  {
    path: '/system/permissions',
    name: 'system-permissions',
    component: () => import('@/views/system/PermissionView.vue'),
    meta: { requiresAuth: true, permission: 'platform.permission.read', titleKey: 'nav.permissions' }
  },
  {
    path: '/system/dict',
    name: 'system-dict',
    component: () => import('@/views/system/DictManagement.vue'),
    meta: { requiresAuth: true, permission: 'platform.dictionary.read', titleKey: 'nav.dict' }
  },
  {
    path: '/system/i18n',
    name: 'system-i18n',
    component: () => import('@/views/system/I18nManagement.vue'),
    meta: { requiresAuth: true, permission: 'platform.i18n.read', titleKey: 'nav.i18n' }
  },
  {
    path: '/system/config',
    name: 'system-config',
    component: () => import('@/views/system/ConfigManagement.vue'),
    meta: { requiresAuth: true, permission: 'platform.config.read', titleKey: 'nav.config' }
  },
  {
    path: '/system/audit',
    name: 'system-audit',
    component: () => import('@/views/system/AuditLog.vue'),
    meta: { requiresAuth: true, permission: 'audit.audit.read', titleKey: 'nav.audit' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let restored = false

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (!restored) {
    authStore.restore()
    restored = true
  }

  if (to.meta.requiresAuth) {
    if (!authStore.token || authStore.isTokenExpired()) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  if (to.meta.permission && authStore.user) {
    const required = to.meta.permission
    if (!authStore.permissions.includes(required)) {
      next('/dashboard')
      return
    }
  }

  next()
})

export default router
