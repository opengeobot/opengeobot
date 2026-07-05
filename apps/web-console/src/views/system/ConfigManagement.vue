<script setup lang="ts">
// Function: Platform config management view with table, CRUD and version history
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import { listConfigs, createConfig, updateConfig, getConfigHistory } from '@/api/config'
import type {
  Config,
  ConfigHistory,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const configs = ref<Config[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  module: '',
  keyword: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const valueTypeOptions = computed<SelectOption[]>(() => [
  { label: 'string', value: 'string' },
  { label: 'number', value: 'number' },
  { label: 'boolean', value: 'boolean' },
  { label: 'json', value: 'json' }
])

const columns = computed<DataTableColumn[]>(() => [
  { key: 'config_key', title: t('config.config_key'), sortable: true },
  { key: 'config_value', title: t('config.config_value') },
  { key: 'value_type', title: t('config.value_type') },
  { key: 'module', title: t('common.module') },
  { key: 'description', title: t('common.description') },
  { key: 'version', title: t('common.version') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadConfigs(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listConfigs({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      module: filters.module || undefined,
      keyword: filters.keyword || undefined
    })
    configs.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadConfigs()
}

function handleReset(): void {
  filters.module = ''
  filters.keyword = ''
  pagination.value.page_number = 1
  loadConfigs()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadConfigs()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadConfigs()
}

// ---- Create / Edit ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => [
  { key: 'config_key', label: t('config.config_key'), type: 'text', required: true },
  { key: 'config_value', label: t('config.config_value'), type: 'textarea', required: true },
  {
    key: 'value_type',
    label: t('config.value_type'),
    type: 'select',
    required: true,
    options: valueTypeOptions.value
  },
  { key: 'module', label: t('common.module'), type: 'text' },
  { key: 'description', label: t('common.description'), type: 'textarea' }
])

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

function openEdit(row: Config): void {
  formMode.value = 'edit'
  formModel.id = row.id
  formModel.config_key = row.config_key
  formModel.config_value = row.config_value
  formModel.value_type = row.value_type
  formModel.module = row.module
  formModel.description = row.description
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'create') {
      await createConfig({
        config_key: String(data.config_key),
        config_value: String(data.config_value),
        value_type: String(data.value_type),
        module: String(data.module ?? ''),
        description: String(data.description ?? '')
      })
    } else {
      await updateConfig(String(formModel.id), {
        config_value: String(data.config_value ?? ''),
        value_type: String(data.value_type ?? 'string'),
        module: String(data.module ?? ''),
        description: String(data.description ?? '')
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadConfigs()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Version history side panel ----

const historyVisible = ref(false)
const history = ref<ConfigHistory[]>([])
const historyTarget = ref<Config | null>(null)
const historyLoading = ref(false)

async function openHistory(row: Config): Promise<void> {
  historyTarget.value = row
  historyVisible.value = true
  historyLoading.value = true
  errorMsg.value = ''
  try {
    history.value = await getConfigHistory(row.id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<template>
  <div class="config-management">
    <h1 class="page-title">{{ t('config.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('config.config_key')"
        @keyup.enter="handleSearch"
      />
      <input v-model="filters.module" class="filter-input-sm" type="text" :placeholder="t('common.module')" />
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'platform.config.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <div class="config-layout" :class="{ 'with-panel': historyVisible }">
      <div class="config-table-area">
        <DataTable
          :columns="columns"
          :data="configs"
          :loading="loading"
          :pagination="pagination"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        >
          <template #actions="{ row }">
            <div class="action-buttons">
              <button v-permission="'platform.config.manage'" class="btn-link" @click="openEdit(row as unknown as Config)">
                {{ t('common.edit') }}
              </button>
              <button class="btn-link" @click="openHistory(row as unknown as Config)">
                {{ t('common.view_history') }}
              </button>
            </div>
          </template>
        </DataTable>
      </div>

      <aside v-if="historyVisible" class="history-panel">
        <div class="panel-header">
          <span class="panel-title">{{ t('config.history_title') }}</span>
          <button class="panel-close" @click="historyVisible = false">×</button>
        </div>
        <div class="panel-body">
          <p v-if="historyTarget" class="history-key">{{ historyTarget.config_key }}</p>
          <p v-if="historyLoading" class="loading-text">{{ t('common.loading') }}</p>
          <div v-else-if="history.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
          <ul v-else class="history-list">
            <li v-for="h in history" :key="h.id" class="history-item">
              <div class="history-item-header">
                <span class="history-version">v{{ h.version }}</span>
                <span class="history-time">{{ h.updated_at }}</span>
              </div>
              <div class="history-value">{{ h.config_value }}</div>
              <div class="history-meta">{{ t('config.updated_by') }}: {{ h.updated_by }}</div>
            </li>
          </ul>
        </div>
      </aside>
    </div>

    <!-- Create / Edit config -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('config.create_title') : t('config.edit_title')"
      :width="560"
      @close="formVisible = false"
    >
      <FormBuilder
        :fields="formFields"
        :model-value="formModel"
        @submit="handleFormSubmit"
        @cancel="formVisible = false"
      />
    </ModalDialog>
  </div>
</template>

<style scoped>
.config-management {
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

.filter-input-sm {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  min-width: 8rem;
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

.config-layout {
  display: flex;
  gap: 1rem;
}

.config-table-area {
  flex: 1;
  min-width: 0;
}

.history-panel {
  width: 20rem;
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  flex-shrink: 0;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e2e8f0;
  background-color: #f8fafc;
}

.panel-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1e293b;
}

.panel-close {
  border: none;
  background: transparent;
  font-size: 1.25rem;
  color: #64748b;
  cursor: pointer;
  line-height: 1;
}

.panel-body {
  padding: 0.75rem;
  max-height: 30rem;
  overflow-y: auto;
}

.history-key {
  font-family: monospace;
  font-size: 0.8125rem;
  color: #2563eb;
  margin: 0 0 0.75rem;
}

.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.history-item {
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  padding: 0.625rem;
}

.history-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.375rem;
}

.history-version {
  font-size: 0.75rem;
  font-weight: 600;
  color: #2563eb;
  background-color: #dbeafe;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
}

.history-time {
  font-size: 0.7rem;
  color: #94a3b8;
}

.history-value {
  font-size: 0.8125rem;
  color: #1e293b;
  word-break: break-all;
  margin-bottom: 0.25rem;
}

.history-meta {
  font-size: 0.7rem;
  color: #64748b;
}

.loading-text {
  color: #64748b;
  font-size: 0.875rem;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  font-size: 0.875rem;
  padding: 1.5rem;
}

.action-buttons {
  display: flex;
  gap: 0.75rem;
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
</style>
