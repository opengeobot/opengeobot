<script setup lang="ts">
// Function: Policy management view with publish and version history
// Time: 2026-07-05
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listPolicies,
  createPolicy,
  updatePolicy,
  publishPolicy,
  listPolicyVersions
} from '@/api/policy'
import type {
  Policy,
  PolicyVersion,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const policies = ref<Policy[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  keyword: '',
  status: '',
  scope: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'policy_name', title: t('policy.name'), sortable: true },
  { key: 'policy_code', title: t('policy.code') },
  { key: 'status', title: t('common.status') },
  { key: 'version', title: t('common.version') },
  { key: 'scope', title: t('policy.scope') }
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('status.publish.draft'), value: 'draft' },
  { label: t('status.publish.published'), value: 'published' },
  { label: t('status.publish.archived'), value: 'archived' }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadPolicies(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listPolicies({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      scope: filters.scope || undefined
    })
    policies.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadPolicies()
}

function handleReset(): void {
  filters.keyword = ''
  filters.status = ''
  filters.scope = ''
  pagination.value.page_number = 1
  loadPolicies()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadPolicies()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadPolicies()
}

// ---- Create / Edit ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => [
  { key: 'policy_name', label: t('policy.name'), type: 'text', required: true },
  { key: 'policy_code', label: t('policy.code'), type: 'text', required: true },
  { key: 'scope', label: t('policy.scope'), type: 'text' },
  { key: 'description', label: t('common.description'), type: 'textarea' },
  {
    key: 'rules',
    label: t('policy.rules'),
    type: 'textarea',
    placeholder: t('policy.rules_hint')
  }
])

function parseRules(text: unknown): Record<string, unknown> {
  if (!text) return {}
  try {
    return JSON.parse(String(text)) as Record<string, unknown>
  } catch {
    return {}
  }
}

function stringifyRules(rules: Record<string, unknown> | undefined): string {
  if (!rules || Object.keys(rules).length === 0) return ''
  try {
    return JSON.stringify(rules, null, 2)
  } catch {
    return ''
  }
}

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModel.rules = ''
  formVisible.value = true
}

function openEdit(row: Policy): void {
  formMode.value = 'edit'
  formModel.id = row.id
  formModel.policy_name = row.policy_name
  formModel.policy_code = row.policy_code
  formModel.scope = row.scope
  formModel.description = row.description
  formModel.rules = stringifyRules(row.rules)
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'create') {
      await createPolicy({
        policy_code: String(data.policy_code),
        policy_name: String(data.policy_name),
        description: String(data.description ?? ''),
        scope: String(data.scope ?? ''),
        rules: parseRules(data.rules)
      })
    } else {
      await updatePolicy(String(formModel.id), {
        policy_name: String(data.policy_name),
        description: String(data.description ?? ''),
        scope: String(data.scope ?? ''),
        rules: parseRules(data.rules)
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadPolicies()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Publish ----

async function handlePublish(row: Policy): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await publishPolicy(row.id)
    successMsg.value = t('common.operation_success')
    await loadPolicies()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Version history ----

const historyVisible = ref(false)
const history = ref<PolicyVersion[]>([])
const historyTarget = ref<Policy | null>(null)
const historyLoading = ref(false)

async function openHistory(row: Policy): Promise<void> {
  historyTarget.value = row
  historyVisible.value = true
  historyLoading.value = true
  errorMsg.value = ''
  try {
    history.value = await listPolicyVersions(row.id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  loadPolicies()
})
</script>

<template>
  <div class="policy-management">
    <h1 class="page-title">{{ t('policy.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('policy.name')"
        @keyup.enter="handleSearch"
      />
      <input v-model="filters.scope" class="filter-input-sm" type="text" :placeholder="t('policy.scope')" />
      <select v-model="filters.status" class="filter-select">
        <option v-for="opt in statusFilterOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'platform.policy.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <div class="policy-layout" :class="{ 'with-panel': historyVisible }">
      <div class="policy-table-area">
        <DataTable
          :columns="columns"
          :data="policies"
          :loading="loading"
          :pagination="pagination"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        >
          <template #cell-status="{ row }">
            <StatusTag :status="row.status as string" type="publish" />
          </template>
          <template #actions="{ row }">
            <div class="action-buttons">
              <button v-permission="'platform.policy.manage'" class="btn-link" @click="openEdit(row as unknown as Policy)">
                {{ t('common.edit') }}
              </button>
              <button v-permission="'platform.policy.manage'" class="btn-link" @click="handlePublish(row as unknown as Policy)">
                {{ t('common.publish') }}
              </button>
              <button class="btn-link" @click="openHistory(row as unknown as Policy)">
                {{ t('common.view_history') }}
              </button>
            </div>
          </template>
        </DataTable>
      </div>

      <aside v-if="historyVisible" class="history-panel">
        <div class="panel-header">
          <span class="panel-title">{{ t('policy.version_title') }}</span>
          <button class="panel-close" @click="historyVisible = false">×</button>
        </div>
        <div class="panel-body">
          <p v-if="historyTarget" class="history-key">{{ historyTarget.policy_name }}</p>
          <p v-if="historyLoading" class="loading-text">{{ t('common.loading') }}</p>
          <div v-else-if="history.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
          <ul v-else class="history-list">
            <li v-for="h in history" :key="h.id" class="history-item">
              <div class="history-item-header">
                <span class="history-version">v{{ h.version }}</span>
                <StatusTag :status="h.status" type="publish" />
                <span class="history-time">{{ h.published_at }}</span>
              </div>
              <div class="history-meta">{{ t('policy.published_by') }}: {{ h.published_by }}</div>
            </li>
          </ul>
        </div>
      </aside>
    </div>

    <!-- Create / Edit policy -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('policy.create_title') : t('policy.edit_title')"
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
.policy-management {
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

.policy-layout {
  display: flex;
  gap: 1rem;
}

.policy-table-area {
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
  font-weight: 600;
  font-size: 0.875rem;
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
  gap: 0.5rem;
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
  margin-left: auto;
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
</style>
