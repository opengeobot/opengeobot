// Function: Vue Router configuration
// Time: 2026-07-04
// Author: AxeXie
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    permission?: string
    titleKey?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { titleKey: 'login.title' }
  },
  {
    path: '/',
    component: DefaultLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'system', redirect: '/system/users' },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { requiresAuth: true, titleKey: 'nav.dashboard' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { requiresAuth: true, permission: 'platform.profile.read', titleKey: 'nav.profile' }
      },
      {
        path: 'robots',
        name: 'robots',
        component: () => import('@/views/RobotManagementView.vue'),
        meta: { requiresAuth: true, permission: 'robot.robot.read', titleKey: 'nav.robots' }
      },
      {
        path: 'robots/:robotId',
        name: 'robot-detail',
        component: () => import('@/views/RobotDetailView.vue'),
        meta: { requiresAuth: true, permission: 'robot.robot.read', titleKey: 'robot.detail_title' }
      },
      {
        path: 'robot-models',
        name: 'robot-models',
        component: () => import('@/views/RobotModelManagementView.vue'),
        meta: { requiresAuth: true, permission: 'robot.model.read', titleKey: 'nav.robot_models' }
      },
      {
        path: 'robot-groups',
        name: 'robot-groups',
        component: () => import('@/views/RobotGroupManagementView.vue'),
        meta: { requiresAuth: true, permission: 'robot.group.read', titleKey: 'nav.robot_groups' }
      },
      {
        path: 'edge-gateways',
        name: 'edge-gateways',
        component: () => import('@/views/EdgeGatewayListView.vue'),
        meta: { requiresAuth: true, permission: 'edge.gateway.read', titleKey: 'nav.edge_gateways' }
      },
      {
        path: 'edge-gateways/:gatewayId',
        name: 'edge-gateway-detail',
        component: () => import('@/views/EdgeGatewayDetailView.vue'),
        meta: { requiresAuth: true, permission: 'edge.gateway.read', titleKey: 'edge.detail_title' }
      },
      {
        path: 'skills',
        name: 'skills',
        component: () => import('@/views/SkillManagementView.vue'),
        meta: { requiresAuth: true, permission: 'skill.skill.read', titleKey: 'nav.skills' }
      },
      {
        path: 'skills/:skillId',
        name: 'skill-detail',
        component: () => import('@/views/SkillDetailView.vue'),
        meta: { requiresAuth: true, permission: 'skill.skill.read', titleKey: 'skill.detail_title' }
      },
      {
        path: 'capabilities',
        name: 'capabilities',
        component: () => import('@/views/CapabilityCatalogView.vue'),
        meta: { requiresAuth: true, permission: 'skill.skill.read', titleKey: 'nav.capabilities' }
      },
      {
        path: 'mcp',
        name: 'mcp',
        component: () => import('@/views/McpManagementView.vue'),
        meta: { requiresAuth: true, permission: 'mcp.tool.read', titleKey: 'nav.mcp' }
      },
      {
        path: 'missions',
        name: 'missions',
        component: () => import('@/views/MissionManagementView.vue'),
        meta: { requiresAuth: true, permission: 'mission.mission.read', titleKey: 'nav.missions' }
      },
      {
        path: 'missions/create',
        name: 'mission-create',
        component: () => import('@/views/MissionCreateView.vue'),
        meta: { requiresAuth: true, permission: 'mission.mission.read', titleKey: 'mission.create_title' }
      },
      {
        path: 'missions/:missionId',
        name: 'mission-detail',
        component: () => import('@/views/MissionDetailView.vue'),
        meta: { requiresAuth: true, permission: 'mission.mission.read', titleKey: 'mission.detail_title' }
      },
      {
        path: 'mission-templates',
        name: 'mission-templates',
        component: () => import('@/views/MissionTemplateManagementView.vue'),
        meta: { requiresAuth: true, permission: 'mission.mission.read', titleKey: 'nav.mission_templates' }
      },
      {
        path: 'mission-approvals',
        name: 'mission-approvals',
        component: () => import('@/views/MissionApprovalView.vue'),
        meta: { requiresAuth: true, permission: 'mission.mission.read', titleKey: 'nav.mission_approvals' }
      },
      {
        path: 'policies',
        name: 'policies',
        component: () => import('@/views/PolicyManagementView.vue'),
        meta: { requiresAuth: true, permission: 'policy.policy.read', titleKey: 'nav.policies' }
      },
      {
        path: 'safety',
        name: 'safety',
        component: () => import('@/views/SafetyControlView.vue'),
        meta: { requiresAuth: true, permission: 'safety.decision.read', titleKey: 'nav.safety' }
      },
      {
        path: 'maps',
        name: 'maps',
        component: () => import('@/views/MapManagementView.vue'),
        meta: { requiresAuth: true, permission: 'map.map.read', titleKey: 'nav.maps' }
      },
      {
        path: 'maps/:mapId',
        name: 'map-detail',
        component: () => import('@/views/MapDetailView.vue'),
        meta: { requiresAuth: true, permission: 'map.map.read', titleKey: 'map.detail_title' }
      },
      {
        path: 'restricted-areas',
        name: 'restricted-areas',
        component: () => import('@/views/RestrictedAreaManagementView.vue'),
        meta: { requiresAuth: true, permission: 'map.map.read', titleKey: 'nav.restricted_areas' }
      },
      {
        path: 'monitor',
        name: 'monitor',
        component: () => import('@/views/MonitorView.vue'),
        meta: { requiresAuth: true, permission: 'monitor.robot.view', titleKey: 'nav.monitor' }
      },
      {
        path: 'monitor/fleet',
        name: 'monitor-fleet',
        component: () => import('@/views/FleetMonitorView.vue'),
        meta: { requiresAuth: true, permission: 'monitor.robot.view', titleKey: 'monitor.fleet_title' }
      },
      {
        path: 'monitor/robots/:robotId',
        name: 'monitor-robot',
        component: () => import('@/views/RobotMonitorView.vue'),
        meta: { requiresAuth: true, permission: 'monitor.robot.view', titleKey: 'monitor.robot_detail' }
      },
      {
        path: 'control/:robotId',
        name: 'control-lease',
        component: () => import('@/views/ControlLeaseView.vue'),
        meta: { requiresAuth: true, permission: 'robot.robot.control', titleKey: 'control.title' }
      },
      {
        path: 'media',
        name: 'media',
        component: () => import('@/views/MediaLibraryView.vue'),
        meta: { requiresAuth: true, permission: 'media.asset.read', titleKey: 'nav.media' }
      },
      {
        path: 'fleet',
        name: 'fleet',
        component: () => import('@/views/FleetManagementView.vue'),
        meta: { requiresAuth: true, permission: 'fleet.schedule.read', titleKey: 'nav.fleet' }
      },
      {
        path: 'alarms',
        name: 'alarms',
        component: () => import('@/views/AlarmManagementView.vue'),
        meta: { requiresAuth: true, permission: 'ops.alarm.read', titleKey: 'nav.alarms' }
      },
      {
        path: 'ops',
        name: 'ops',
        component: () => import('@/views/OpsDashboardView.vue'),
        meta: { requiresAuth: true, permission: 'dashboard.view', titleKey: 'nav.ops' }
      },
      {
        path: 'ota',
        name: 'ota',
        component: () => import('@/views/OtaManagementView.vue'),
        meta: { requiresAuth: true, permission: 'ops.ota.read', titleKey: 'nav.ota' }
      },
      {
        path: 'recovery',
        name: 'recovery',
        component: () => import('@/views/BackupRecoveryView.vue'),
        meta: { requiresAuth: true, permission: 'ops.backup.read', titleKey: 'nav.recovery' }
      },
      {
        path: 'memory',
        name: 'memory',
        component: () => import('@/views/TaskMemoryView.vue'),
        meta: { requiresAuth: true, permission: 'memory.memory.read', titleKey: 'nav.memory' }
      },
      {
        path: 'failure-cases',
        name: 'failure-cases',
        component: () => import('@/views/FailureCaseView.vue'),
        meta: { requiresAuth: true, permission: 'memory.failure_case.read', titleKey: 'nav.failure_cases' }
      },
      {
        path: 'improvements',
        name: 'improvements',
        component: () => import('@/views/ImprovementManagementView.vue'),
        meta: { requiresAuth: true, permission: 'memory.failure_case.read', titleKey: 'nav.improvements' }
      },
      {
        path: 'trace',
        name: 'trace',
        component: () => import('@/views/TraceView.vue'),
        meta: { requiresAuth: true, permission: 'trace.trace.read', titleKey: 'nav.trace' }
      },
      {
        path: 'traces/:traceId',
        name: 'trace-detail',
        component: () => import('@/views/TraceDetailView.vue'),
        meta: { requiresAuth: true, permission: 'trace.trace.read', titleKey: 'trace.detail' }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('@/views/system/UserManagement.vue'),
        meta: { requiresAuth: true, permission: 'platform.user.read', titleKey: 'nav.users' }
      },
      {
        path: 'system/orgs',
        name: 'system-orgs',
        component: () => import('@/views/system/OrgManagement.vue'),
        meta: { requiresAuth: true, permission: 'platform.org.manage', titleKey: 'nav.orgs' }
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('@/views/system/RoleManagement.vue'),
        meta: { requiresAuth: true, permission: 'platform.role.read', titleKey: 'nav.roles' }
      },
      {
        path: 'system/permissions',
        name: 'system-permissions',
        component: () => import('@/views/system/PermissionView.vue'),
        meta: { requiresAuth: true, permission: 'platform.permission.read', titleKey: 'nav.permissions' }
      },
      {
        path: 'system/dict',
        name: 'system-dict',
        component: () => import('@/views/system/DictManagement.vue'),
        meta: { requiresAuth: true, permission: 'platform.dictionary.read', titleKey: 'nav.dict' }
      },
      {
        path: 'system/i18n',
        name: 'system-i18n',
        component: () => import('@/views/system/I18nManagement.vue'),
        meta: { requiresAuth: true, permission: 'platform.i18n.read', titleKey: 'nav.i18n' }
      },
      {
        path: 'system/config',
        name: 'system-config',
        component: () => import('@/views/system/ConfigManagement.vue'),
        meta: { requiresAuth: true, permission: 'platform.config.read', titleKey: 'nav.config' }
      },
      {
        path: 'system/audit',
        name: 'system-audit',
        component: () => import('@/views/system/AuditLog.vue'),
        meta: { requiresAuth: true, permission: 'audit.audit.read', titleKey: 'nav.audit' }
      }
    ]
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
    const required = to.meta.permission as string
    if (!authStore.permissions.includes(required)) {
      console.warn(`[Router] Permission denied: requires "${required}", user has [${authStore.permissions.slice(0, 5).join(', ')}...${authStore.permissions.length} total]`)
      next('/dashboard')
      return
    }
  }

  next()
})

export default router
