<!--
  Function: Mission template management page
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import { listMissionTemplates, createMissionTemplate } from '@/api/mission'
import type {
  MissionTemplate,
  FormField,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const rows = ref<MissionTemplate[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const pagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const hasPermission = computed(() => authStore.permissions.includes('mission.mission.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'template_name', title: t('mission.template_name'), sortable: true },
  { key: 'template_code', title: t('mission.template_code') },
  { key: 'description', title: t('common.description') }
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
    const result = await listMissionTemplates({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size
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

const formVisible = ref(false)
const formModel = reactive<Record<string, unknown>>({
  steps_json: '[{"action":"navigate","target":"home","parameters":{}}]'
})

const formFields = computed<FormField[]>(() => [
  { key: 'template_code', label: t('mission.template_code'), type: 'text', required: true },
  { key: 'template_name', label: t('mission.template_name'), type: 'text', required: true },
  { key: 'description', label: t('common.description'), type: 'textarea' },
  { key: 'steps_json', label: t('mission.steps') + ' (JSON)', type: 'textarea', required: true }
])

async function handleCreate(values: Record<string, unknown>): Promise<void> {
  try {
    const steps = JSON.parse(String(values.steps_json))
    await createMissionTemplate({
      template_code: String(values.template_code),
      template_name: String(values.template_name),
      description: String(values.description ?? ''),
      steps
    })
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    if (err instanceof SyntaxError) {
      errorMsg.value = t('mission.invalid_steps')
    } else {
      errorMsg.value = resolveError(err as ProblemDetails)
    }
  }
}

onMounted(() => {
  if (hasPermission.value) loadRows()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('mission.template_title') }}</h1>
    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('mission.forbidden') }}</div>
    <template v-else>
      <div class="toolbar">
        <button class="btn btn-secondary" @click="loadRows">{{ t('common.refresh') }}</button>
        <button class="btn btn-primary" @click="formVisible = true">{{ t('mission.create_template') }}</button>
      </div>
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>
      <p v-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
      <p v-else-if="viewState === 'EMPTY'" class="empty-text">{{ t('common.no_data') }}</p>
      <DataTable
        v-if="viewState === 'READY' || rows.length > 0"
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        @page-change="(p) => { pagination.page_number = p; loadRows() }"
        @size-change="(s) => { pagination.page_size = s; pagination.page_number = 1; loadRows() }"
      />
    </template>

    <ModalDialog :visible="formVisible" :title="t('mission.create_template')" :width="560" @close="formVisible = false">
      <FormBuilder :fields="formFields" :model-value="formModel" @submit="handleCreate" @cancel="formVisible = false" />
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.toolbar { display: flex; gap: 0.5rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text, .empty-text { color: #64748b; }
</style>
