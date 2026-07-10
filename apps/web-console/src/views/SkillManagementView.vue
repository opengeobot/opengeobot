<script setup lang="ts">
// Function: Skill / capability management view with publish and version history
// Time: 2026-07-05
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listSkills,
  createSkill,
  updateSkill,
  publishSkill,
  disableSkill,
  enableSkill,
  getSkillVersions
} from '@/api/skill'
import type {
  Skill,
  SkillVersion,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()

const skills = ref<Skill[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  keyword: '',
  module: '',
  status: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('skill.name'), sortable: true },
  { key: 'module', title: t('common.module') },
  { key: 'module', title: t('common.module') },
  { key: 'status', title: t('common.status') },
  { key: 'current_version', title: t('skill.current_version') }
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('status.publish.draft'), value: 'draft' },
  { label: t('status.publish.published'), value: 'published' },
  { label: t('status.publish.disabled'), value: 'disabled' }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadSkills(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listSkills({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      keyword: filters.keyword || undefined,
      module: filters.module || undefined,
      status: filters.status || undefined
    })
    skills.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadSkills()
}

function handleReset(): void {
  filters.keyword = ''
  filters.module = ''
  filters.status = ''
  pagination.value.page_number = 1
  loadSkills()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadSkills()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadSkills()
}

// ---- Create / Edit ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => [
  { key: 'name', label: t('skill.name'), type: 'text', required: true },
  { key: 'module', label: t('common.module'), type: 'text' },
  { key: 'description', label: t('common.description'), type: 'textarea' }
])

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

function openEdit(row: Skill): void {
  formMode.value = 'edit'
  formModel.skill_id = row.skill_id
  formModel.name = row.name
  formModel.module = row.module
  formModel.description = row.description
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'create') {
      await createSkill({
        name: String(data.name),
        module: String(data.module ?? ''),
        description: String(data.description ?? '')
      })
    } else {
      await updateSkill(String(formModel.skill_id), {
        description: String(data.description ?? '')
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadSkills()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Lifecycle actions ----

async function handlePublish(row: Skill): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await publishSkill(row.skill_id)
    successMsg.value = t('common.operation_success')
    await loadSkills()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleDisable(row: Skill): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await disableSkill(row.skill_id)
    successMsg.value = t('common.operation_success')
    await loadSkills()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleEnable(row: Skill): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await enableSkill(row.skill_id)
    successMsg.value = t('common.operation_success')
    await loadSkills()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Version history ----

const historyVisible = ref(false)
const history = ref<SkillVersion[]>([])
const historyTarget = ref<Skill | null>(null)
const historyLoading = ref(false)

async function openHistory(row: Skill): Promise<void> {
  historyTarget.value = row
  historyVisible.value = true
  historyLoading.value = true
  errorMsg.value = ''
  try {
    history.value = await getSkillVersions(row.skill_id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  loadSkills()
})
</script>

<template>
  <div class="skill-management">
    <h1 class="page-title">{{ t('skill.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('skill.name')"
        @keyup.enter="handleSearch"
      />
      <input v-model="filters.module" class="filter-input-sm" type="text" :placeholder="t('common.module')" />
      <select v-model="filters.status" class="filter-select">
        <option v-for="opt in statusFilterOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'platform.skill.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <div class="skill-layout" :class="{ 'with-panel': historyVisible }">
      <div class="skill-table-area">
        <DataTable
          :columns="columns"
          :data="skills"
          :loading="loading"
          :pagination="pagination"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        >
          <template #cell-name="{ row }">
            <button class="btn-link" @click="router.push(`/skills/${(row as unknown as Skill).skill_id}`)">
              {{ (row as unknown as Skill).name }}
            </button>
          </template>
          <template #cell-status="{ row }">
            <StatusTag :status="row.status as string" type="publish" />
          </template>
          <template #actions="{ row }">
            <div class="action-buttons">
              <button class="btn-link" @click="router.push(`/skills/${(row as unknown as Skill).skill_id}`)">
                {{ t('common.view_detail') }}
              </button>
              <button v-permission="'platform.skill.manage'" class="btn-link" @click="openEdit(row as unknown as Skill)">
                {{ t('common.edit') }}
              </button>
              <button v-permission="'platform.skill.manage'" class="btn-link" @click="handlePublish(row as unknown as Skill)">
                {{ t('common.publish') }}
              </button>
              <button
                v-permission="'platform.skill.manage'"
                class="btn-link"
                @click="(row as unknown as Skill).status === 'disabled' ? handleEnable(row as unknown as Skill) : handleDisable(row as unknown as Skill)"
              >
                {{ (row as unknown as Skill).status === 'disabled' ? t('common.enable') : t('common.disable') }}
              </button>
              <button class="btn-link" @click="openHistory(row as unknown as Skill)">
                {{ t('common.view_history') }}
              </button>
            </div>
          </template>
        </DataTable>
      </div>

      <aside v-if="historyVisible" class="history-panel">
        <div class="panel-header">
          <span class="panel-title">{{ t('skill.version_title') }}</span>
          <button class="panel-close" @click="historyVisible = false">×</button>
        </div>
        <div class="panel-body">
          <p v-if="historyTarget" class="history-key">{{ historyTarget.name }}</p>
          <p v-if="historyLoading" class="loading-text">{{ t('common.loading') }}</p>
          <div v-else-if="history.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
          <ul v-else class="history-list">
            <li v-for="h in history" :key="h.id" class="history-item">
              <div class="history-item-header">
                <span class="history-version">v{{ h.version }}</span>
                <StatusTag :status="h.status" type="publish" />
                <span class="history-time">{{ h.published_at }}</span>
              </div>
              <div class="history-value">{{ h.change_log }}</div>
              <div class="history-meta">{{ t('skill.published_by') }}: {{ h.published_by }}</div>
            </li>
          </ul>
        </div>
      </aside>
    </div>

    <!-- Create / Edit skill -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('skill.create_title') : t('skill.edit_title')"
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
  </div>
</template>

<style scoped>
.skill-management {
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

.skill-layout {
  display: flex;
  gap: 1rem;
}

.skill-table-area {
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

.history-value {
  font-size: 0.8125rem;
  color: #1e293b;
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
