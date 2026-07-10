<!--
  Function: Control lease acquire/release and takeover for a robot
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'
import {
  getRobot,
  getActiveControlLease,
  acquireControlLease,
  releaseControlLease
} from '@/api/robot'
import { takeover } from '@/api/monitor'
import type { Robot, ControlLease, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const robotId = computed(() => String(route.params.robotId ?? ''))
const robot = ref<Robot | null>(null)
const lease = ref<ControlLease | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const acquireForm = reactive({ ttl_seconds: 300, reason: '' })

const hasPermission = computed(() => authStore.permissions.includes('robot.robot.control'))

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

async function loadData(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    robot.value = await getRobot(robotId.value)
    lease.value = await getActiveControlLease(robotId.value)
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

async function handleAcquire(): Promise<void> {
  try {
    lease.value = await acquireControlLease(robotId.value, {
      ttl_seconds: Number(acquireForm.ttl_seconds) || 300,
      reason: acquireForm.reason || undefined
    })
    successMsg.value = t('control.acquired')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleRelease(): Promise<void> {
  try {
    lease.value = await releaseControlLease(robotId.value)
    successMsg.value = t('control.released')
    lease.value = await getActiveControlLease(robotId.value)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleTakeover(): Promise<void> {
  try {
    await takeover(robotId.value, {
      operator_id: authStore.user?.user_id ?? 'operator',
      reason: acquireForm.reason || 'takeover'
    })
    lease.value = await getActiveControlLease(robotId.value)
    successMsg.value = t('common.operation_success')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
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
      <h1 class="page-title">{{ t('control.title') }}</h1>
      <div class="header-actions">
        <button class="btn btn-secondary" @click="router.push(`/robots/${robotId}`)">{{ t('robot.detail_title') }}</button>
        <button class="btn btn-secondary" @click="loadData">{{ t('common.refresh') }}</button>
      </div>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('control.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else>
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

      <section v-if="robot" class="card">
        <h2 class="section-title">{{ robot.name }}</h2>
        <div class="info-grid">
          <div><span class="label">ID</span><span class="mono">{{ robot.id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="robot.status" type="robot" /></div>
        </div>
      </section>

      <section class="card">
        <h2 class="section-title">{{ t('control.lease') }}</h2>
        <div v-if="lease" class="info-grid">
          <div><span class="label">{{ t('control.lease_id') }}</span><span class="mono">{{ lease.lease_id }}</span></div>
          <div><span class="label">{{ t('control.holder') }}</span><span>{{ lease.holder_user_id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="lease.status" type="enable-disable" /></div>
          <div><span class="label">{{ t('control.acquired_at') }}</span><span>{{ lease.acquired_at }}</span></div>
          <div><span class="label">{{ t('control.expires_at') }}</span><span>{{ lease.expires_at }}</span></div>
          <div><span class="label">{{ t('control.fencing_token') }}</span><span class="mono">{{ lease.fencing_token || '-' }}</span></div>
        </div>
        <p v-else class="empty-text">{{ t('control.no_lease') }}</p>

        <div class="form-row">
          <label class="field">
            <span>{{ t('control.ttl') }}</span>
            <input v-model.number="acquireForm.ttl_seconds" class="input" type="number" min="30" max="3600" />
          </label>
          <label class="field grow">
            <span>{{ t('control.reason') }}</span>
            <input v-model="acquireForm.reason" class="input" type="text" />
          </label>
        </div>

        <div class="actions">
          <button class="btn btn-primary" @click="handleAcquire">{{ t('control.acquire') }}</button>
          <button class="btn btn-secondary" :disabled="!lease" @click="handleRelease">{{ t('control.release') }}</button>
          <button class="btn btn-primary" @click="handleTakeover">{{ t('monitor.takeover') }}</button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.header-actions { display: flex; gap: 0.5rem; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.section-title { font-size: 1rem; font-weight: 600; margin: 0 0 0.75rem; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); gap: 1rem; }
.label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.25rem; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; word-break: break-all; }
.form-row { display: flex; gap: 0.75rem; margin-top: 1rem; flex-wrap: wrap; }
.field { display: flex; flex-direction: column; gap: 0.375rem; font-size: 0.875rem; color: #475569; }
.field.grow { flex: 1; min-width: 12rem; }
.input { padding: 0.5rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 0.375rem; }
.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 1rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text, .empty-text { color: #64748b; }
</style>
