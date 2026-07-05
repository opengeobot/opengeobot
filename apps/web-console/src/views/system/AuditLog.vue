<script setup lang="ts">
// Function: Audit log view with filters, detail modal and async export
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listAudits } from '@/api/audit'
import { createExport, getExport, downloadExport } from '@/api/export'
import type {
  AuditLog,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const audits = ref<AuditLog[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  actor_id: '',
  action: '',
  resource_type: '',
  trace_id: '',
  start_time: '',
  end_time: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'occurred_at', title: t('common.occurred_at'), sortable: true, width: 160 },
  { key: 'actor', title: t('common.actor') },
  { key: 'action', title: t('common.action') },
  { key: 'resource_type', title: t('common.resource_type') },
  { key: 'resource_id', title: t('common.resource_id') },
  { key: 'result', title: t('common.result') },
  { key: 'trace_id', title: t('common.trace_id'), width: 140 }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadAudits(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listAudits({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      actor_id: filters.actor_id || undefined,
      action: filters.action || undefined,
      resource_type: filters.resource_type || undefined,
      trace_id: filters.trace_id || undefined,
      start_time: filters.start_time || undefined,
      end_time: filters.end_time || undefined
    })
    audits.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadAudits()
}

function handleReset(): void {
  filters.actor_id = ''
  filters.action = ''
  filters.resource_type = ''
  filters.trace_id = ''
  filters.start_time = ''
  filters.end_time = ''
  pagination.value.page_number = 1
  loadAudits()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadAudits()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadAudits()
}

// ---- Detail modal ----

const detailVisible = ref(false)
const detailRow = ref<AuditLog | null>(null)

function openDetail(row: AuditLog): void {
  detailRow.value = row
  detailVisible.value = true
}

// ---- Export ----

const exporting = ref(false)

async function handleExport(): Promise<void> {
  exporting.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const task = await createExport({
      resource_type: 'audit',
      filters: {
        actor_id: filters.actor_id || undefined,
        action: filters.action || undefined,
        resource_type: filters.resource_type || undefined,
        trace_id: filters.trace_id || undefined,
        start_time: filters.start_time || undefined,
        end_time: filters.end_time || undefined
      }
    })
    successMsg.value = t('audit.export_started')
    pollExport(task.id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    exporting.value = false
  }
}

async function pollExport(taskId: string): Promise<void> {
  let attempts = 0
  const maxAttempts = 60
  const interval = setInterval(async () => {
    attempts += 1
    try {
      const task = await getExport(taskId)
      if (task.status === 'succeeded' && task.file_url) {
        clearInterval(interval)
        await downloadExport(taskId, `audit-export-${Date.now()}.csv`)
      } else if (task.status === 'failed') {
        clearInterval(interval)
        errorMsg.value = t('common.operation_failed')
      }
    } catch {
      clearInterval(interval)
    }
    if (attempts >= maxAttempts) {
      clearInterval(interval)
    }
  }, 2000)
}

onMounted(() => {
  loadAudits()
})
</script>

<template>
  <div class="audit-log-view">
    <h1 class="page-title">{{ t('audit.title') }}</h1>

    <div class="filter-grid">
      <div class="filter-field">
        <label class="filter-label">{{ t('common.actor') }}</label>
        <input v-model="filters.actor_id" type="text" class="filter-input" />
      </div>
      <div class="filter-field">
        <label class="filter-label">{{ t('common.action') }}</label>
        <input v-model="filters.action" type="text" class="filter-input" />
      </div>
      <div class="filter-field">
        <label class="filter-label">{{ t('common.resource_type') }}</label>
        <input v-model="filters.resource_type" type="text" class="filter-input" />
      </div>
      <div class="filter-field">
        <label class="filter-label">{{ t('common.trace_id') }}</label>
        <input v-model="filters.trace_id" type="text" class="filter-input" />
      </div>
      <div class="filter-field">
        <label class="filter-label">{{ t('common.start_time') }}</label>
        <input v-model="filters.start_time" type="datetime-local" class="filter-input" />
      </div>
      <div class="filter-field">
        <label class="filter-label">{{ t('common.end_time') }}</label>
        <input v-model="filters.end_time" type="datetime-local" class="filter-input" />
      </div>
    </div>

    <div class="toolbar">
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button class="btn btn-secondary" :disabled="exporting" @click="handleExport">
        {{ exporting ? t('common.loading') : t('common.export') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="audits"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #cell-result="{ value }">
        <StatusTag :status="value as string" type="task" />
      </template>
      <template #cell-trace_id="{ value }">
        <span class="trace-cell">{{ value }}</span>
      </template>
      <template #actions="{ row }">
        <div class="action-buttons">
          <button class="btn-link" @click="openDetail(row as unknown as AuditLog)">
            {{ t('common.view_detail') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Detail modal -->
    <ModalDialog
      :visible="detailVisible"
      :title="t('audit.detail_title')"
      :width="640"
      @close="detailVisible = false"
    >
      <div v-if="detailRow" class="detail-content">
        <div class="detail-grid">
          <div class="detail-field">
            <span class="field-label">{{ t('common.occurred_at') }}</span>
            <span class="field-value">{{ detailRow.occurred_at }}</span>
          </div>
          <div class="detail-field">
            <span class="field-label">{{ t('common.actor') }}</span>
            <span class="field-value">{{ detailRow.actor }}</span>
          </div>
          <div class="detail-field">
            <span class="field-label">{{ t('common.action') }}</span>
            <span class="field-value">{{ detailRow.action }}</span>
          </div>
          <div class="detail-field">
            <span class="field-label">{{ t('common.resource_type') }}</span>
            <span class="field-value">{{ detailRow.resource_type }}</span>
          </div>
          <div class="detail-field">
            <span class="field-label">{{ t('common.resource_id') }}</span>
            <span class="field-value">{{ detailRow.resource_id }}</span>
          </div>
          <div class="detail-field">
            <span class="field-label">{{ t('common.result') }}</span>
            <StatusTag :status="detailRow.result" type="task" />
          </div>
          <div class="detail-field detail-field-wide">
            <span class="field-label">{{ t('common.trace_id') }}</span>
            <span class="field-value trace-cell">{{ detailRow.trace_id }}</span>
          </div>
        </div>

        <div class="payload-section">
          <h3 class="payload-title">{{ t('audit.payload_before') }}</h3>
          <pre class="payload-block">{{ detailRow.payload_before || '—' }}</pre>
        </div>
        <div class="payload-section">
          <h3 class="payload-title">{{ t('audit.payload_after') }}</h3>
          <pre class="payload-block">{{ detailRow.payload_after || '—' }}</pre>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="detailVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.audit-log-view {
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

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: 0.75rem;
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.filter-label {
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 500;
}

.filter-input {
  padding: 0.5rem 0.625rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
}

.toolbar {
  display: flex;
  gap: 0.5rem;
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

.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

.trace-cell {
  font-family: monospace;
  font-size: 0.75rem;
  color: #64748b;
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
}

.btn-link {
  background: transparent;
  border: none;
  color: #2563eb;
  font-size: 0.8125rem;
  cursor: pointer;
  padding: 0;
}

.btn-link:hover {
  text-decoration: underline;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.75rem;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-field-wide {
  grid-column: span 2;
}

.field-label {
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 500;
}

.field-value {
  font-size: 0.875rem;
  color: #1e293b;
  word-break: break-all;
}

.payload-section {
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  overflow: hidden;
}

.payload-title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #475569;
  padding: 0.5rem 0.75rem;
  background-color: #f8fafc;
  margin: 0;
  border-bottom: 1px solid #e2e8f0;
}

.payload-block {
  margin: 0;
  padding: 0.75rem;
  font-size: 0.75rem;
  font-family: monospace;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 12rem;
  overflow-y: auto;
  background-color: #fafbfc;
}
</style>
