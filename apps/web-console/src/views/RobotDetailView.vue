<!--
  Function: Robot detail — info, capabilities and control lease link
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'
import { getRobot, getRobotCapabilities } from '@/api/robot'
import type { Robot, RobotCapability, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const robotId = computed(() => String(route.params.robotId ?? ''))
const robot = ref<Robot | null>(null)
const capabilities = ref<RobotCapability[]>([])
const loading = ref(false)
const errorMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() => authStore.permissions.includes('robot.robot.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !robot.value) return 'LOADING'
  if (errorMsg.value && !robot.value) return 'ERROR'
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
    robot.value = await getRobot(robotId.value)
    try {
      capabilities.value = await getRobotCapabilities(robotId.value)
    } catch {
      capabilities.value = robot.value.capabilities ?? []
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
  if (hasPermission.value) loadDetail()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('robot.detail_title') }}</h1>
      <div class="header-actions">
        <button class="btn btn-primary" @click="router.push(`/control/${robotId}`)">
          {{ t('control.open') }}
        </button>
        <button class="btn btn-secondary" @click="router.push(`/monitor/robots/${robotId}`)">
          {{ t('monitor.robot_detail') }}
        </button>
        <button class="btn btn-secondary" @click="router.push('/robots')">{{ t('common.cancel') }}</button>
      </div>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('robot.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="robot">
      <section class="card">
        <div class="info-grid">
          <div><span class="label">{{ t('robot.name') }}</span><span>{{ robot.name }}</span></div>
          <div><span class="label">ID</span><span class="mono">{{ robot.robot_id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="robot.status" type="robot" /></div>
          <div><span class="label">{{ t('robot.model') }}</span><span>{{ robot.model_name || robot.model_id }}</span></div>
          <div><span class="label">{{ t('robot.serial_number') }}</span><span>{{ robot.serial_number }}</span></div>
          <div><span class="label">{{ t('robot.org') }}</span><span>{{ robot.org_name || robot.org_id }}</span></div>
          <div><span class="label">{{ t('robot.last_seen') }}</span><span>{{ robot.last_seen || '-' }}</span></div>
          <div><span class="label">{{ t('common.created_at') }}</span><span>{{ robot.created_at }}</span></div>
        </div>
      </section>

      <section class="card">
        <h2 class="section-title">{{ t('robot.capabilities_title') }}</h2>
        <p v-if="capabilities.length === 0" class="empty-text">{{ t('common.no_data') }}</p>
        <ul v-else class="caps-list">
          <li v-for="cap in capabilities" :key="cap.capability_type + ':' + cap.capability_value">
            {{ cap.capability_type }}{{ cap.capability_value ? ` = ${cap.capability_value}` : '' }}
          </li>
        </ul>
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
.section-title { font-size: 1rem; font-weight: 600; margin: 0 0 0.75rem; }
.caps-list { margin: 0; padding-left: 1.25rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.loading-text, .empty-text { color: #64748b; }
</style>
