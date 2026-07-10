<!--
  Function: Robot model CRUD management page
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
import {
  listRobotModels,
  createRobotModel,
  updateRobotModel,
  deleteRobotModel
} from '@/api/robot'
import type {
  RobotModel,
  FormField,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const rows = ref<RobotModel[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const pagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const hasPermission = computed(() => authStore.permissions.includes('robot.model.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'model_name', title: t('robot_model.name'), sortable: true },
  { key: 'model_id', title: t('robot_model.id') },
  { key: 'manufacturer', title: t('robot_model.manufacturer') },
  { key: 'description', title: t('common.description') },
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
    const result = await listRobotModels({
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
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const formModel = reactive<Record<string, unknown>>({})
const deleteVisible = ref(false)
const deleteTarget = ref<RobotModel | null>(null)

const formFields = computed<FormField[]>(() => {
  const fields: FormField[] = []
  if (formMode.value === 'create') {
    fields.push({ key: 'model_name', label: t('robot_model.name'), type: 'text', required: true })
  }
  fields.push(
    { key: 'manufacturer', label: t('robot_model.manufacturer'), type: 'text' },
    { key: 'description', label: t('common.description'), type: 'textarea' },
    { key: 'capabilities', label: t('robot.capabilities'), type: 'textarea', placeholder: t('robot.capabilities_hint') }
  )
  return fields
})

function openCreate(): void {
  formMode.value = 'create'
  editingId.value = ''
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

function openEdit(row: RobotModel): void {
  formMode.value = 'edit'
  editingId.value = row.model_id
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModel.manufacturer = row.manufacturer
  formModel.description = row.description
  formModel.capabilities = row.capabilities ?? ''
  formVisible.value = true
}

async function handleSubmit(values: Record<string, unknown>): Promise<void> {
  try {
    if (formMode.value === 'create') {
      await createRobotModel({
        model_name: String(values.model_name),
        manufacturer: values.manufacturer ? String(values.manufacturer) : undefined,
        description: values.description ? String(values.description) : undefined,
        capabilities: values.capabilities ? String(values.capabilities) : undefined
      })
    } else {
      await updateRobotModel(editingId.value, {
        manufacturer: values.manufacturer ? String(values.manufacturer) : undefined,
        description: values.description ? String(values.description) : undefined,
        capabilities: values.capabilities ? String(values.capabilities) : undefined
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

function openDelete(row: RobotModel): void {
  deleteTarget.value = row
  deleteVisible.value = true
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  try {
    await deleteRobotModel(deleteTarget.value.model_id)
    deleteVisible.value = false
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
    <h1 class="page-title">{{ t('robot_model.title') }}</h1>
    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('robot_model.forbidden') }}</div>
    <template v-else>
      <div class="toolbar">
        <button class="btn btn-secondary" @click="loadRows">{{ t('common.refresh') }}</button>
        <button v-permission="'robot.model.manage'" class="btn btn-primary" @click="openCreate">
          {{ t('common.create') }}
        </button>
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
      >
        <template #actions="{ row }">
          <div class="action-buttons">
            <button v-permission="'robot.model.manage'" class="btn-link" @click="openEdit(row as unknown as RobotModel)">
              {{ t('common.edit') }}
            </button>
            <button v-permission="'robot.model.manage'" class="btn-link btn-danger" @click="openDelete(row as unknown as RobotModel)">
              {{ t('common.delete') }}
            </button>
          </div>
        </template>
      </DataTable>
    </template>

    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('robot_model.create_title') : t('robot_model.edit_title')"
      :width="480"
      @close="formVisible = false"
    >
      <FormBuilder :fields="formFields" :model-value="formModel" @submit="handleSubmit" @cancel="formVisible = false" />
    </ModalDialog>

    <ModalDialog :visible="deleteVisible" :title="t('common.delete')" :width="400" @close="deleteVisible = false">
      <p>{{ t('common.confirm_delete') }}</p>
      <template #footer>
        <button class="btn btn-danger" @click="confirmDelete">{{ t('common.delete') }}</button>
        <button class="btn btn-secondary" @click="deleteVisible = false">{{ t('common.cancel') }}</button>
      </template>
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
.btn-danger { background-color: #dc2626; color: #fff; }
.btn-link { background: transparent; border: none; color: #2563eb; cursor: pointer; font-size: 0.8125rem; padding: 0; }
.btn-link.btn-danger { color: #dc2626; }
.action-buttons { display: flex; gap: 0.75rem; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text, .empty-text { color: #64748b; }
</style>
