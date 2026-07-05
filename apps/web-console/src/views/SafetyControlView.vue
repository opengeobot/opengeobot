<script setup lang="ts">
// Function: Safety control view with e-stop, reset, state and event log
// Time: 2026-07-05
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  emergencyStop,
  resetSafety,
  getSafetyState,
  listSafetyEvents
} from '@/api/safety'
import type {
  SafetyState,
  SafetyEvent,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const state = ref<SafetyState | null>(null)
const events = ref<SafetyEvent[]>([])
const loading = ref(false)
const stateLoading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  robot_id: '',
  event_type: '',
  level: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'occurred_at', title: t('common.occurred_at'), sortable: true },
  { key: 'robot_id', title: t('safety.robot') },
  { key: 'event_type', title: t('safety.event_type') },
  { key: 'level', title: t('safety.level') },
  { key: 'source', title: t('safety.source') },
  { key: 'resolved', title: t('safety.resolved') },
  { key: 'trace_id', title: t('common.trace_id') }
])

const levelOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('safety.level_info'), value: 'info' },
  { label: t('safety.level_warning'), value: 'warning' },
  { label: t('safety.level_critical'), value: 'critical' }
])

const eStopped = computed<boolean>(() => !!state.value?.e_stopped)
const locked = computed<boolean>(() => !!state.value?.locked)

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadState(): Promise<void> {
  stateLoading.value = true
  errorMsg.value = ''
  try {
    state.value = await getSafetyState(filters.robot_id || undefined)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    stateLoading.value = false
  }
}

async function loadEvents(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listSafetyEvents({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      robot_id: filters.robot_id || undefined,
      event_type: filters.event_type || undefined,
      level: filters.level || undefined
    })
    events.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadEvents()
}

function handleResetFilters(): void {
  filters.robot_id = ''
  filters.event_type = ''
  filters.level = ''
  pagination.value.page_number = 1
  loadState()
  loadEvents()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadEvents()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadEvents()
}

// ---- E-stop / reset ----

const stopModalVisible = ref(false)
const resetModalVisible = ref(false)
const actionReason = ref('')

function openStop(): void {
  actionReason.value = ''
  stopModalVisible.value = true
}

async function handleStopConfirm(): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    state.value = await emergencyStop({
      robot_id: filters.robot_id || undefined,
      reason: actionReason.value || undefined
    })
    stopModalVisible.value = false
    successMsg.value = t('safety.estop_triggered')
    await loadEvents()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

function openReset(): void {
  actionReason.value = ''
  resetModalVisible.value = true
}

async function handleResetConfirm(): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    state.value = await resetSafety({
      robot_id: filters.robot_id || undefined
    })
    resetModalVisible.value = false
    successMsg.value = t('safety.reset_done')
    await loadEvents()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadState()
  loadEvents()
})
</script>

<template>
  <div class="safety-control">
    <h1 class="page-title">{{ t('safety.title') }}</h1>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <div class="safety-top">
      <div class="state-card" :class="{ 'state-danger': eStopped, 'state-warn': locked && !eStopped }">
        <div class="state-header">
          <span class="state-title">{{ t('safety.state_title') }}</span>
          <span v-if="stateLoading" class="loading-text">{{ t('common.loading') }}</span>
        </div>
        <div v-if="state" class="state-body">
          <div class="state-row">
            <span class="state-label">{{ t('safety.estop_status') }}</span>
            <StatusTag :status="eStopped ? 'failed' : 'succeeded'" type="task" />
          </div>
          <div class="state-row">
            <span class="state-label">{{ t('safety.locked') }}</span>
            <StatusTag :status="locked ? 'enabled' : 'disabled'" type="enable-disable" />
          </div>
          <div class="state-row">
            <span class="state-label">{{ t('safety.robot') }}</span>
            <span class="state-value">{{ state.robot_id || t('common.all') }}</span>
          </div>
          <div class="state-row">
            <span class="state-label">{{ t('safety.reason') }}</span>
            <span class="state-value">{{ state.reason || '—' }}</span>
          </div>
          <div class="state-row">
            <span class="state-label">{{ t('common.updated_at') }}</span>
            <span class="state-value">{{ state.updated_at }}</span>
          </div>
        </div>
        <div v-else class="state-empty">{{ t('common.no_data') }}</div>
      </div>

      <div class="action-card">
        <button class="btn-estop" @click="openStop">
          <span class="estop-icon">⛔</span>
          <span class="estop-text">{{ t('safety.estop') }}</span>
        </button>
        <button class="btn-reset" @click="openReset">
          {{ t('safety.reset') }}
        </button>
      </div>
    </div>

    <div class="toolbar">
      <input v-model="filters.robot_id" class="filter-input-sm" type="text" :placeholder="t('safety.robot')" />
      <input v-model="filters.event_type" class="filter-input-sm" type="text" :placeholder="t('safety.event_type')" />
      <select v-model="filters.level" class="filter-select">
        <option v-for="opt in levelOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleResetFilters">{{ t('common.reset') }}</button>
    </div>

    <DataTable
      :columns="columns"
      :data="events"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #cell-level="{ value }">
        <StatusTag :status="(value as string)" type="health" />
      </template>
      <template #cell-resolved="{ value }">
        <span>{{ value ? t('common.yes') : t('common.no') }}</span>
      </template>
    </DataTable>

    <!-- E-stop confirm -->
    <ModalDialog
      :visible="stopModalVisible"
      :title="t('safety.estop')"
      :width="420"
      @close="stopModalVisible = false"
    >
      <p class="confirm-text alert alert-error">{{ t('safety.estop_confirm') }}</p>
      <div class="form-field">
        <label class="form-label">{{ t('safety.reason') }}</label>
        <textarea v-model="actionReason" class="form-input form-textarea" />
      </div>
      <template #footer>
        <button class="btn btn-danger" @click="handleStopConfirm">{{ t('safety.estop') }}</button>
        <button class="btn btn-secondary" @click="stopModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Reset confirm -->
    <ModalDialog
      :visible="resetModalVisible"
      :title="t('safety.reset')"
      :width="400"
      @close="resetModalVisible = false"
    >
      <p class="confirm-text">{{ t('safety.reset_confirm') }}</p>
      <template #footer>
        <button class="btn btn-primary" @click="handleResetConfirm">{{ t('safety.reset') }}</button>
        <button class="btn btn-secondary" @click="resetModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.safety-control {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.safety-top {
  display: flex;
  gap: 1rem;
  align-items: stretch;
  flex-wrap: wrap;
}

.state-card {
  flex: 1;
  min-width: 18rem;
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-left-width: 4px;
  border-left-color: #16a34a;
  border-radius: 0.5rem;
  padding: 1rem 1.25rem;
}

.state-card.state-danger {
  border-left-color: #dc2626;
  background-color: #fef2f2;
}

.state-card.state-warn {
  border-left-color: #d97706;
  background-color: #fffbeb;
}

.state-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.state-title {
  font-size: 1rem;
  font-weight: 600;
  color: #1e293b;
}

.state-body {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.state-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.875rem;
}

.state-label {
  width: 6.5rem;
  color: #64748b;
}

.state-value {
  color: #1e293b;
}

.state-empty {
  color: #94a3b8;
  font-size: 0.875rem;
  padding: 0.5rem 0;
}

.action-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: center;
  justify-content: center;
  min-width: 12rem;
}

.btn-estop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  width: 100%;
  padding: 1.25rem 1.5rem;
  border: none;
  border-radius: 0.5rem;
  background-color: #dc2626;
  color: #ffffff;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.15s;
}

.btn-estop:hover {
  background-color: #b91c1c;
}

.estop-icon {
  font-size: 1.75rem;
}

.btn-reset {
  width: 100%;
  padding: 0.75rem 1.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.5rem;
  background-color: #ffffff;
  color: #334155;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-reset:hover {
  background-color: #f1f5f9;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.filter-input-sm {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  min-width: 9rem;
}

.filter-select {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
}

.btn {
  padding: 0.5rem 1.25rem;
  border: none;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background-color: #2563eb;
  color: #ffffff;
}

.btn-primary:hover {
  background-color: #1d4ed8;
}

.btn-secondary {
  background-color: #f1f5f9;
  color: #475569;
}

.btn-secondary:hover {
  background-color: #e2e8f0;
}

.btn-danger {
  background-color: #dc2626;
  color: #ffffff;
}

.btn-danger:hover {
  background-color: #b91c1c;
}

.alert {
  padding: 0.625rem 0.875rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
}

.alert-error {
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
}

.alert-success {
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #16a34a;
}

.confirm-text {
  font-size: 0.875rem;
  color: #334155;
  margin: 0;
}

.loading-text {
  color: #64748b;
  font-size: 0.8125rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  margin-top: 0.75rem;
}

.form-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #334155;
}

.form-input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  outline: none;
}

.form-textarea {
  min-height: 4rem;
  resize: vertical;
}
</style>
