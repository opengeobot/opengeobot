<!--
  Function: Mission approval queue for WAITING_APPROVAL missions
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listMissions, approveMission, rejectMission } from '@/api/mission'
import type {
  Mission,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const rows = ref<Mission[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const pagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })
const rejectVisible = ref(false)
const rejectTarget = ref<Mission | null>(null)
const rejectReason = ref('')

const hasPermission = computed(() =>
  authStore.permissions.includes('mission.mission.approve') ||
  authStore.permissions.includes('mission.mission.read')
)

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('mission.name'), sortable: true },
  { key: 'robot_name', title: t('mission.robot') },
  { key: 'status', title: t('common.status') },
  { key: 'priority', title: t('mission.priority') },
  { key: 'created_at', title: t('common.created_at') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadRows(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listMissions({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      status: 'READY'
    })
    rows.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

function missionIdOf(row: Mission): string {
  return row.mission_id || row.id || ''
}

async function handleApprove(row: Mission): Promise<void> {
  try {
    await approveMission(missionIdOf(row))
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

function openReject(row: Mission): void {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject(): Promise<void> {
  if (!rejectTarget.value || !rejectReason.value.trim()) return
  try {
    await rejectMission(missionIdOf(rejectTarget.value), { reason: rejectReason.value.trim() })
    rejectVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  if (hasPermission.value) loadRows()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('mission.approvals_title') }}</h1>
    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('mission.forbidden') }}</div>
    <template v-else>
      <div class="toolbar">
        <button class="btn btn-secondary" @click="loadRows">{{ t('common.refresh') }}</button>
      </div>
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>
      <p v-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
      <p v-else-if="viewState === 'EMPTY'" class="empty-text">{{ t('mission.no_approvals') }}</p>
      <DataTable
        v-if="viewState === 'READY' || rows.length > 0"
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        @page-change="(p) => { pagination.page_number = p; loadRows() }"
        @size-change="(s) => { pagination.page_size = s; pagination.page_number = 1; loadRows() }"
      >
        <template #cell-name="{ row }">
          <button class="btn-link" @click="router.push(`/missions/${missionIdOf(row as unknown as Mission)}`)">
            {{ (row as unknown as Mission).name }}
          </button>
        </template>
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="task" />
        </template>
        <template #actions="{ row }">
          <div class="action-buttons">
            <button class="btn-link" @click="router.push(`/missions/${missionIdOf(row as unknown as Mission)}`)">
              {{ t('common.view_detail') }}
            </button>
            <button
              v-if="authStore.permissions.includes('mission.mission.approve')"
              class="btn-link"
              @click="handleApprove(row as unknown as Mission)"
            >
              {{ t('mission.approve') }}
            </button>
            <button
              v-if="authStore.permissions.includes('mission.mission.approve')"
              class="btn-link btn-danger"
              @click="openReject(row as unknown as Mission)"
            >
              {{ t('mission.reject') }}
            </button>
          </div>
        </template>
      </DataTable>
    </template>

    <ModalDialog :visible="rejectVisible" :title="t('mission.reject')" :width="420" @close="rejectVisible = false">
      <label class="field">
        <span>{{ t('mission.reject_reason') }}</span>
        <textarea v-model="rejectReason" class="input" rows="3" />
      </label>
      <template #footer>
        <button class="btn btn-danger" @click="confirmReject">{{ t('mission.reject') }}</button>
        <button class="btn btn-secondary" @click="rejectVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.toolbar { display: flex; gap: 0.5rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.btn-danger { background-color: #dc2626; color: #fff; }
.btn-link { background: transparent; border: none; color: #2563eb; cursor: pointer; font-size: 0.8125rem; padding: 0; }
.btn-link.btn-danger { color: #dc2626; }
.action-buttons { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text, .empty-text { color: #64748b; }
.field { display: flex; flex-direction: column; gap: 0.375rem; font-size: 0.875rem; }
.input { padding: 0.5rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 0.375rem; }
</style>
