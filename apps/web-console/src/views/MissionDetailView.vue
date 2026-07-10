<!--
  Function: Mission detail with execution and approval controls
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  getMission,
  startMission,
  pauseMission,
  resumeMission,
  cancelMission,
  submitApproval,
  approveMission,
  rejectMission
} from '@/api/mission'
import type { Mission, DataTableColumn, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const missionId = computed(() => String(route.params.missionId ?? ''))
const mission = ref<Mission | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const rejectVisible = ref(false)
const rejectReason = ref('')

const hasPermission = computed(() => authStore.permissions.includes('mission.mission.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !mission.value) return 'LOADING'
  if (errorMsg.value && !mission.value) return 'ERROR'
  return 'READY'
})

const stepColumns = computed<DataTableColumn[]>(() => [
  { key: 'step_index', title: '#' },
  { key: 'action', title: t('mission.step_action') },
  { key: 'target', title: t('mission.step_target') },
  { key: 'status', title: t('common.status') }
])

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
    mission.value = await getMission(missionId.value)
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

async function runAction(action: () => Promise<Mission>): Promise<void> {
  errorMsg.value = ''
  try {
    mission.value = await action()
    successMsg.value = t('common.operation_success')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleReject(): Promise<void> {
  if (!rejectReason.value.trim()) return
  await runAction(() => rejectMission(missionId.value, { reason: rejectReason.value.trim() }))
  rejectVisible.value = false
  rejectReason.value = ''
}

onMounted(() => {
  if (hasPermission.value) loadDetail()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('mission.detail_title') }}</h1>
      <button class="btn btn-secondary" @click="router.push('/missions')">{{ t('common.cancel') }}</button>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('mission.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="mission">
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

      <section class="card">
        <div class="info-grid">
          <div><span class="label">{{ t('mission.name') }}</span><span>{{ mission.name }}</span></div>
          <div><span class="label">ID</span><span class="mono">{{ mission.id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="mission.status" type="task" /></div>
          <div><span class="label">{{ t('mission.robot') }}</span><span>{{ mission.robot_name || mission.robot_id }}</span></div>
          <div><span class="label">{{ t('mission.priority') }}</span><span>{{ mission.priority }}</span></div>
          <div><span class="label">{{ t('common.description') }}</span><span>{{ mission.description || '-' }}</span></div>
        </div>

        <div class="actions">
          <button class="btn btn-primary" @click="runAction(() => startMission(missionId))">{{ t('mission.start') }}</button>
          <button class="btn btn-secondary" @click="runAction(() => pauseMission(missionId))">{{ t('mission.pause') }}</button>
          <button class="btn btn-secondary" @click="runAction(() => resumeMission(missionId))">{{ t('mission.resume') }}</button>
          <button class="btn btn-danger" @click="runAction(() => cancelMission(missionId))">{{ t('mission.cancel') }}</button>
          <button class="btn btn-secondary" @click="runAction(() => submitApproval(missionId))">{{ t('mission.submit_approval') }}</button>
          <button v-permission="'mission.mission.approve'" class="btn btn-primary" @click="runAction(() => approveMission(missionId))">
            {{ t('mission.approve') }}
          </button>
          <button v-permission="'mission.mission.approve'" class="btn btn-secondary" @click="rejectVisible = true">
            {{ t('mission.reject') }}
          </button>
        </div>
      </section>

      <section class="card">
        <h2 class="section-title">{{ t('mission.steps') }}</h2>
        <DataTable :columns="stepColumns" :data="mission.steps ?? []">
          <template #cell-status="{ row }">
            <StatusTag :status="row.status as string" type="task" />
          </template>
        </DataTable>
      </section>
    </template>

    <ModalDialog :visible="rejectVisible" :title="t('mission.reject')" :width="420" @close="rejectVisible = false">
      <label class="field">
        <span>{{ t('mission.reject_reason') }}</span>
        <textarea v-model="rejectReason" class="input" rows="3" />
      </label>
      <template #footer>
        <button class="btn btn-danger" @click="handleReject">{{ t('mission.reject') }}</button>
        <button class="btn btn-secondary" @click="rejectVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); gap: 1rem; }
.label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.25rem; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; }
.section-title { font-size: 1rem; font-weight: 600; margin: 0 0 0.75rem; }
.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 1.25rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.btn-danger { background-color: #dc2626; color: #fff; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text { color: #64748b; }
.field { display: flex; flex-direction: column; gap: 0.375rem; font-size: 0.875rem; }
.input { padding: 0.5rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 0.375rem; }
</style>
