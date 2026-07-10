<!--
  Function: Single robot monitor detail from getRobotMonitor
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'
import { getRobotMonitor, takeover } from '@/api/monitor'
import type { RobotMonitor, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const robotId = computed(() => String(route.params.robotId ?? ''))
const monitor = ref<RobotMonitor | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() => authStore.permissions.includes('monitor.robot.view'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !monitor.value) return 'LOADING'
  if (errorMsg.value && !monitor.value) return 'ERROR'
  return 'READY'
})

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadDetail(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    monitor.value = await getRobotMonitor(robotId.value)
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

async function handleTakeover(): Promise<void> {
  try {
    monitor.value = await takeover(robotId.value, {
      operator_id: authStore.user?.user_id ?? 'operator',
      reason: 'manual takeover from monitor'
    })
    successMsg.value = t('common.operation_success')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  if (hasPermission.value) loadDetail()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('monitor.robot_detail') }}</h1>
      <div class="header-actions">
        <button class="btn btn-primary" @click="router.push(`/control/${robotId}`)">{{ t('control.open') }}</button>
        <button class="btn btn-secondary" @click="loadDetail">{{ t('common.refresh') }}</button>
        <button class="btn btn-secondary" @click="router.push('/monitor/fleet')">{{ t('common.cancel') }}</button>
      </div>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('monitor.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="monitor">
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>
      <section class="card">
        <div class="info-grid">
          <div><span class="label">{{ t('robot.name') }}</span><span>{{ monitor.robot_name }}</span></div>
          <div><span class="label">ID</span><span class="mono">{{ monitor.robot_id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="monitor.status" type="robot" /></div>
          <div><span class="label">{{ t('monitor.battery') }}</span><span>{{ monitor.battery }}%</span></div>
          <div>
            <span class="label">{{ t('monitor.position') }}</span>
            <span>x={{ monitor.position?.x }}, y={{ monitor.position?.y }}, yaw={{ monitor.position?.yaw }}</span>
          </div>
          <div><span class="label">{{ t('mission.name') }}</span><span>{{ monitor.current_mission_id || '-' }}</span></div>
          <div><span class="label">{{ t('robot.last_seen') }}</span><span>{{ monitor.last_seen }}</span></div>
        </div>
        <div class="actions">
          <button v-permission="'robot.robot.control'" class="btn btn-primary" @click="handleTakeover">
            {{ t('monitor.takeover') }}
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.header-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); gap: 1rem; }
.label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.25rem; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; }
.actions { margin-top: 1.25rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text { color: #64748b; }
</style>
