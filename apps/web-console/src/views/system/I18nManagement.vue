<script setup lang="ts">
// Function: I18n resource management view with filters, CRUD and CSV batch import
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import {
  listI18nResources,
  createI18nResource,
  updateI18nResource,
  deleteI18nResource,
  batchImportI18n
} from '@/api/i18n'
import type {
  I18nResource,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const resources = ref<I18nResource[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  locale: '',
  module: '',
  keyword: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const localeOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
])

const columns = computed<DataTableColumn[]>(() => [
  { key: 'resource_key', title: t('i18n.resource_key'), sortable: true },
  { key: 'locale', title: t('i18n.locale') },
  { key: 'resource_value', title: t('i18n.resource_value') },
  { key: 'module', title: t('common.module') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadResources(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listI18nResources({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      locale: filters.locale || undefined,
      module: filters.module || undefined,
      keyword: filters.keyword || undefined
    })
    resources.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadResources()
}

function handleReset(): void {
  filters.locale = ''
  filters.module = ''
  filters.keyword = ''
  pagination.value.page_number = 1
  loadResources()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadResources()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadResources()
}

// ---- Create / Edit ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => [
  { key: 'resource_key', label: t('i18n.resource_key'), type: 'text', required: true },
  {
    key: 'locale',
    label: t('i18n.locale'),
    type: 'select',
    required: true,
    options: localeOptions.value.filter((o) => o.value !== '')
  },
  { key: 'resource_value', label: t('i18n.resource_value'), type: 'textarea', required: true },
  { key: 'module', label: t('common.module'), type: 'text' }
])

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

function openEdit(row: I18nResource): void {
  formMode.value = 'edit'
  formModel.id = row.id
  formModel.resource_key = row.resource_key
  formModel.locale = row.locale
  formModel.resource_value = row.resource_value
  formModel.module = row.module
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'create') {
      await createI18nResource({
        resource_key: String(data.resource_key),
        locale: String(data.locale),
        resource_value: String(data.resource_value),
        module: String(data.module ?? '')
      })
    } else {
      await updateI18nResource(String(formModel.id), {
        resource_value: String(data.resource_value ?? ''),
        module: String(data.module ?? '')
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadResources()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleDelete(row: I18nResource): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  if (!confirm(t('common.confirm_delete'))) return
  try {
    await deleteI18nResource(row.id)
    successMsg.value = t('common.operation_success')
    await loadResources()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Batch import ----

const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)

function openImport(): void {
  importFile.value = null
  importVisible.value = true
}

function handleFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    importFile.value = target.files[0]
  }
}

async function handleImportConfirm(): Promise<void> {
  if (!importFile.value) return
  importing.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const result = await batchImportI18n(importFile.value)
    importVisible.value = false
    successMsg.value = t('i18n.import_success', { count: result.imported })
    await loadResources()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  loadResources()
})
</script>

<template>
  <div class="i18n-management">
    <h1 class="page-title">{{ t('i18n.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('i18n.resource_key')"
        @keyup.enter="handleSearch"
      />
      <select v-model="filters.locale" class="filter-select">
        <option v-for="opt in localeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <input v-model="filters.module" class="filter-input-sm" type="text" :placeholder="t('common.module')" />
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'platform.i18n.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
      <button v-permission="'platform.i18n.manage'" class="btn btn-secondary" @click="openImport">
        {{ t('common.import') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="resources"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #actions="{ row }">
        <div class="action-buttons">
          <button v-permission="'platform.i18n.manage'" class="btn-link" @click="openEdit(row as unknown as I18nResource)">
            {{ t('common.edit') }}
          </button>
          <button v-permission="'platform.i18n.manage'" class="btn-link btn-danger" @click="handleDelete(row as unknown as I18nResource)">
            {{ t('common.delete') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Create / Edit resource -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('i18n.create_title') : t('i18n.edit_title')"
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

    <!-- Batch import -->
    <ModalDialog
      :visible="importVisible"
      :title="t('i18n.batch_import_title')"
      :width="440"
      @close="importVisible = false"
    >
      <div class="import-content">
        <label class="import-label">{{ t('i18n.select_file') }}</label>
        <input type="file" accept=".csv" class="file-input" @change="handleFileChange" />
      </div>
      <template #footer>
        <button class="btn btn-primary" :disabled="!importFile || importing" @click="handleImportConfirm">
          {{ importing ? t('common.loading') : t('common.confirm') }}
        </button>
        <button class="btn btn-secondary" @click="importVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.i18n-management {
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
  opacity: 0.6;
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

.btn-link.btn-danger {
  color: #dc2626;
}

.import-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.import-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #334155;
}

.file-input {
  font-size: 0.875rem;
}
</style>
