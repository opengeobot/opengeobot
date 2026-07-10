<!--
  Function: Fleet monitor overview cards from getOverview
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getOverview } from '@/api/monitor'
import { listRobots } from '@/api/robot'
import type { MonitorOverview, Robot, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const overview = ref<MonitorOverview | null>(null)
const robots = ref<Robot[]>([])
const loading = ref(false)
const errorMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() => authStore.permissions.includes('monitor.robot.view'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !overview.value) return 'LOADING'
  if (errorMsg.value && !overview.value) return 'ERROR'
  return 'READY'
})

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadData(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    overview.value = await getOverview()
    try {
      const result = await listRobots({ page_number: 1, page_size: 50 })
      robots.value = result.items
    } catch {
      robots.value = []
    }
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (hasPermission.value) loadData()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('monitor.fleet_title') }}</h1>
      <button class="btn btn-secondary" @click="loadData">{{ t('common.refresh') }}</button>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('monitor.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="overview">
      <div class="cards">
        <div class="stat-card"><span class="stat-label">{{ t('monitor.total_robots') }}</span><span class="stat-value">{{ overview.total_robots }}</span></div>
        <div class="stat-card"><span class="stat-label">{{ t('monitor.online_robots') }}</span><span class="stat-value">{{ overview.online_robots }}</span></div>
        <div class="stat-card"><span class="stat-label">{{ t('monitor.busy_robots') }}</span><span class="stat-value">{{ overview.busy_robots }}</span></div>
        <div class="stat-card"><span class="stat-label">{{ t('monitor.active_missions') }}</span><span class="stat-value">{{ overview.active_missions }}</span></div>
        <div class="stat-card alert-card"><span class="stat-label">{{ t('monitor.alerts') }}</span><span class="stat-value">{{ overview.alerts }}</span></div>
      </div>

      <section class="card">
        <h2 class="section-title">{{ t('monitor.robot_grid') }}</h2>
        <div v-if="robots.length === 0" class="empty-text">{{ t('common.no_data') }}</div>
        <div v-else class="robot-grid">
          <button
            v-for="robot in robots"
            :key="robot.id"
            class="robot-tile"
            @click="router.push(`/monitor/robots/${robot.id}`)"
          >
            <span class="robot-name">{{ robot.name }}</span>
            <span class="robot-status">{{ robot.status }}</span>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr)); gap: 0.75rem; }
.stat-card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1rem; background: #f8fafc; }
.alert-card { border-color: #fecaca; background: #fef2f2; }
.stat-label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.375rem; }
.stat-value { font-size: 1.5rem; font-weight: 700; color: #1e293b; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.section-title { font-size: 1rem; font-weight: 600; margin: 0 0 0.75rem; }
.robot-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr)); gap: 0.75rem; }
.robot-tile { display: flex; flex-direction: column; gap: 0.25rem; padding: 0.875rem; border: 1px solid #e2e8f0; border-radius: 0.5rem; background: #fff; cursor: pointer; text-align: left; }
.robot-tile:hover { border-color: #2563eb; }
.robot-name { font-weight: 600; color: #1e293b; }
.robot-status { font-size: 0.75rem; color: #64748b; text-transform: uppercase; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.loading-text, .empty-text { color: #64748b; }
</style>
