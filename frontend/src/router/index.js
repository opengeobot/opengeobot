import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/components/layout/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    children: [
      {
        path: '',
        name: 'Overview',
        component: () => import('@/views/Overview.vue'),
        meta: { title: '项目概览' }
      },
      {
        path: 'assets',
        name: 'Assets',
        component: () => import('@/views/Assets.vue'),
        meta: { title: '资产中心' }
      },
      {
        path: 'prompts',
        name: 'Prompts',
        component: () => import('@/views/Prompts.vue'),
        meta: { title: '提示词库' }
      },
      {
        path: 'runs',
        name: 'Runs',
        component: () => import('@/views/Runs.vue'),
        meta: { title: '运行中心' }
      },
      {
        path: 'insights',
        name: 'Insights',
        component: () => import('@/views/Insights.vue'),
        meta: { title: '洞察中心' }
      },
      {
        path: 'bot',
        name: 'BotWorkbench',
        component: () => import('@/views/BotWorkbench.vue'),
        meta: { title: 'Bot 工作台' }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/Reports.vue'),
        meta: { title: '效果报告' }
      },
      {
        path: 'monitoring',
        name: 'Monitoring',
        component: () => import('@/views/Monitoring.vue'),
        meta: { title: '监控告警' }
      },
      {
        path: 'strategy-memory',
        name: 'StrategyMemory',
        component: () => import('@/views/StrategyMemory.vue'),
        meta: { title: '策略记忆库' }
      },
      // Phase 3: 多租户与权限
      {
        path: 'tenant-management',
        name: 'TenantManagement',
        component: () => import('@/views/TenantManagement.vue'),
        meta: { title: '多租户管理' }
      },
      // Phase 3.3: 外部引用建设
      {
        path: 'reference-tasks',
        name: 'ReferenceTasks',
        component: () => import('@/views/ReferenceTasks.vue'),
        meta: { title: '引用建设' }
      },
      // Phase 3.4: 自动实验设计
      {
        path: 'experiments',
        name: 'Experiments',
        component: () => import('@/views/Experiments.vue'),
        meta: { title: '实验设计' }
      },
      // Phase 3.5: 策略复用与 Benchmark
      {
        path: 'strategy-templates',
        name: 'StrategyTemplates',
        component: () => import('@/views/StrategyTemplates.vue'),
        meta: { title: '策略模板库' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} - OpenGEO Bot`
  }
  next()
})

export default router
