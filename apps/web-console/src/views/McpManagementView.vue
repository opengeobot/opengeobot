<script setup lang="ts">
// Function: MCP tool management view with register, detail, invoke and history
// Time: 2026-07-09
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listMcpTools,
  registerMcpTool,
  invokeMcpTool,
  listInvocations
} from '@/api/mcp'
import type {
  McpTool,
  McpInvocation,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const tools = ref<McpTool[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  keyword: '',
  status: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'tool_name', title: t('mcp.tool_name'), sortable: true },
  { key: 'tool_code', title: t('common.code') },
  { key: 'status', title: t('mcp.status') },
  { key: 'description', title: t('mcp.description') }
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('mcp.active'), value: 'active' },
  { label: t('mcp.inactive'), value: 'inactive' }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadTools(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listMcpTools({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined
    })
    tools.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadTools()
}

function handleReset(): void {
  filters.keyword = ''
  filters.status = ''
  pagination.value.page_number = 1
  loadTools()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadTools()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadTools()
}

// ---- Register ----

const formVisible = ref(false)
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => [
  { key: 'tool_code', label: t('common.code'), type: 'text', required: true },
  { key: 'tool_name', label: t('mcp.tool_name'), type: 'text', required: true },
  { key: 'description', label: t('common.description'), type: 'textarea' },
  { key: 'input_schema', label: t('mcp.input_schema'), type: 'textarea' }
])

function openCreate(): void {
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModel.input_schema = '{}'
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    let schema: Record<string, unknown> = {}
    const raw = String(data.input_schema ?? '{}')
    if (raw.trim()) {
      schema = JSON.parse(raw) as Record<string, unknown>
    }
    await registerMcpTool({
      tool_code: String(data.tool_code),
      tool_name: String(data.tool_name),
      description: String(data.description ?? ''),
      input_schema: schema
    })
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadTools()
  } catch (err) {
    if (err instanceof SyntaxError) {
      errorMsg.value = t('mcp.invalid_json')
    } else {
      errorMsg.value = resolveError(err as ProblemDetails)
    }
  }
}

// ---- Detail ----

const detailVisible = ref(false)
const detailTarget = ref<McpTool | null>(null)

function openDetail(row: McpTool): void {
  detailTarget.value = row
  detailVisible.value = true
}

function formatJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value ?? '')
  }
}

// ---- Invoke / Test ----

const invokeVisible = ref(false)
const invokeTarget = ref<McpTool | null>(null)
const invokeInput = ref('{}')
const invokeResult = ref<McpInvocation | null>(null)
const invokeError = ref('')
const invoking = ref(false)

function openInvoke(row: McpTool): void {
  invokeTarget.value = row
  invokeInput.value = '{}'
  invokeResult.value = null
  invokeError.value = ''
  invokeVisible.value = true
}

function formatOutput(output: string | null): string {
  if (!output) return ''
  try {
    return JSON.stringify(JSON.parse(output), null, 2)
  } catch {
    return output
  }
}

async function handleInvoke(): Promise<void> {
  if (!invokeTarget.value) return
  invoking.value = true
  invokeResult.value = null
  invokeError.value = ''
  try {
    const input = JSON.parse(invokeInput.value) as Record<string, unknown>
    invokeResult.value = await invokeMcpTool(invokeTarget.value.id, { input })
    await loadInvocations()
  } catch (err) {
    if (err instanceof SyntaxError) {
      invokeError.value = t('mcp.invalid_json')
    } else {
      invokeError.value = resolveError(err as ProblemDetails)
    }
  } finally {
    invoking.value = false
  }
}

// ---- Invocation history ----

const invocations = ref<McpInvocation[]>([])
const historyLoading = ref(false)
const historyPagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 5,
  total: 0
})

const historyColumns = computed<DataTableColumn[]>(() => [
  { key: 'tool_name', title: t('mcp.tool_name') },
  { key: 'started_at', title: t('mcp.invoked_at') },
  { key: 'status', title: t('mcp.status') },
  { key: 'duration_ms', title: t('mcp.duration') },
  { key: 'trace_id', title: t('mcp.trace_id') }
])

async function loadInvocations(): Promise<void> {
  historyLoading.value = true
  try {
    const result = await listInvocations({
      page_number: historyPagination.value.page_number,
      page_size: historyPagination.value.page_size
    })
    invocations.value = result.items
    historyPagination.value.total = result.total
  } catch {
    // history is supplementary, ignore errors
  } finally {
    historyLoading.value = false
  }
}

function handleHistoryPageChange(page: number): void {
  historyPagination.value.page_number = page
  loadInvocations()
}

function handleHistorySizeChange(size: number): void {
  historyPagination.value.page_size = size
  historyPagination.value.page_number = 1
  loadInvocations()
}

function computeDuration(inv: McpInvocation): string {
  if (!inv.finished_at) return '-'
  const start = new Date(inv.started_at).getTime()
  const end = new Date(inv.finished_at).getTime()
  const diff = end - start
  if (isNaN(diff)) return '-'
  return String(diff)
}

onMounted(() => {
  loadTools()
  loadInvocations()
})
</script>

<template>
  <div class="mcp-management">
    <h1 class="page-title">{{ t('mcp.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('mcp.tool_name')"
        @keyup.enter="handleSearch"
      />
      <select v-model="filters.status" class="filter-select">
        <option v-for="opt in statusFilterOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'mcp.tool.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('mcp.register_tool') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="tools"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #cell-status="{ row }">
        <StatusTag :status="row.status as string" type="enable-disable" />
      </template>
      <template #actions="{ row }">
        <div class="action-buttons">
          <button class="btn-link" @click="openDetail(row as unknown as McpTool)">
            {{ t('common.view_detail') }}
          </button>
          <button class="btn-link" @click="openInvoke(row as unknown as McpTool)">
            {{ t('mcp.invoke') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Invocation history -->
    <div class="history-section">
      <h2 class="section-title">{{ t('mcp.invocation_history') }}</h2>
      <DataTable
        :columns="historyColumns"
        :data="invocations"
        :loading="historyLoading"
        :pagination="historyPagination"
        @page-change="handleHistoryPageChange"
        @size-change="handleHistorySizeChange"
      >
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="task" />
        </template>
        <template #cell-duration_ms="{ row }">
          {{ computeDuration(row as unknown as McpInvocation) }}
        </template>
      </DataTable>
    </div>

    <!-- Register tool -->
    <ModalDialog
      :visible="formVisible"
      :title="t('mcp.register_tool')"
      :width="520"
      @close="formVisible = false"
    >
      <FormBuilder
        :fields="formFields"
        :model-value="formModel"
        @submit="handleFormSubmit"
        @cancel="formVisible = false"
      />
    </ModalDialog>

    <!-- Detail modal -->
    <ModalDialog
      :visible="detailVisible"
      :title="t('common.view_detail')"
      :width="640"
      @close="detailVisible = false"
    >
      <div v-if="detailTarget" class="detail-content">
        <div class="detail-row">
          <span class="detail-label">{{ t('mcp.tool_name') }}</span>
          <span class="detail-value">{{ detailTarget.tool_name }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">{{ t('common.code') }}</span>
          <span class="detail-value">{{ detailTarget.tool_code }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">{{ t('mcp.status') }}</span>
          <StatusTag :status="detailTarget.status" type="enable-disable" />
        </div>
        <div class="detail-row">
          <span class="detail-label">{{ t('mcp.description') }}</span>
          <span class="detail-value">{{ detailTarget.description || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">{{ t('mcp.input_schema') }}</span>
          <pre class="detail-json">{{ formatJson(detailTarget.input_schema) }}</pre>
        </div>
        <div class="detail-row">
          <span class="detail-label">{{ t('common.created_at') }}</span>
          <span class="detail-value">{{ detailTarget.registered_at }}</span>
        </div>
      </div>
    </ModalDialog>

    <!-- Invoke / Test modal -->
    <ModalDialog
      :visible="invokeVisible"
      :title="t('mcp.invoke')"
      :width="640"
      @close="invokeVisible = false"
    >
      <div class="invoke-content">
        <p v-if="invokeTarget" class="invoke-target">
          {{ invokeTarget.tool_name }}
        </p>
        <label class="invoke-label">{{ t('mcp.input') }}</label>
        <textarea
          v-model="invokeInput"
          class="invoke-textarea"
          rows="8"
        ></textarea>
        <p v-if="invokeError" class="alert alert-error">{{ invokeError }}</p>
        <div v-if="invokeResult" class="invoke-result">
          <div class="detail-row">
            <span class="detail-label">{{ t('mcp.status') }}</span>
            <StatusTag :status="invokeResult.status" type="task" />
          </div>
          <div v-if="invokeResult.output" class="detail-row">
            <span class="detail-label">{{ t('mcp.result') }}</span>
            <pre class="detail-json">{{ formatOutput(invokeResult.output) }}</pre>
          </div>
          <div v-if="invokeResult.error" class="detail-row">
            <span class="detail-label">{{ t('common.error') }}</span>
            <span class="detail-value error-text">{{ invokeResult.error }}</span>
          </div>
          <div v-if="invokeResult.trace_id" class="detail-row">
            <span class="detail-label">{{ t('mcp.trace_id') }}</span>
            <span class="detail-value">{{ invokeResult.trace_id }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="invokeVisible = false">
          {{ t('common.cancel') }}
        </button>
        <button class="btn btn-primary" :disabled="invoking" @click="handleInvoke">
          {{ t('mcp.invoke') }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.mcp-management {
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.filter-input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  min-width: 12rem;
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

.btn-primary:disabled {
  background-color: #93c5fd;
  cursor: not-allowed;
}

.btn-secondary {
  background-color: #f1f5f9;
  color: #475569;
}

.btn-secondary:hover {
  background-color: #e2e8f0;
}

.alert {
  padding: 0.625rem 0.875rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  margin: 0;
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

.history-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 1rem;
}

.section-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.action-buttons {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
  flex-wrap: wrap;
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
  gap: 0.75rem;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
}

.detail-value {
  font-size: 0.875rem;
  color: #1e293b;
}

.error-text {
  color: #dc2626;
}

.detail-json {
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  padding: 0.75rem;
  font-size: 0.8125rem;
  color: #1e293b;
  overflow-x: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.invoke-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.invoke-target {
  font-weight: 600;
  font-size: 0.9rem;
  color: #2563eb;
  margin: 0;
}

.invoke-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
}

.invoke-textarea {
  width: 100%;
  padding: 0.625rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-family: monospace;
  resize: vertical;
  box-sizing: border-box;
}

.invoke-result {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e2e8f0;
}
</style>
