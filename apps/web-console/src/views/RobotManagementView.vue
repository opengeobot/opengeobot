<script setup lang="ts">
// Function: Robot management view with table, filters, CRUD, status and capabilities
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
  listRobots,
  createRobot,
  updateRobot,
  deleteRobot,
  updateRobotStatus,
  getRobotCapabilities,
  updateRobotCapabilities,
  listRobotModels
} from '@/api/robot'
import { listOrgs } from '@/api/org'
import type {
  Robot,
  RobotModel,
  Org,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()

const robots = ref<Robot[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  name: '',
  status: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const orgs = ref<Org[]>([])
const models = ref<RobotModel[]>([])

const columns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('robot.name'), sortable: true },
  { key: 'model_name', title: t('robot.model') },
  { key: 'serial_number', title: t('robot.serial_number') },
  { key: 'status', title: t('common.status') },
  { key: 'org_name', title: t('robot.org') },
  { key: 'last_seen', title: t('robot.last_seen') }
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('status.robot.online'), value: 'online' },
  { label: t('status.robot.offline'), value: 'offline' },
  { label: t('status.robot.busy'), value: 'busy' },
  { label: t('status.robot.maintenance'), value: 'maintenance' },
  { label: t('status.robot.error'), value: 'error' }
])

const statusActionOptions = computed<SelectOption[]>(() => [
  { label: t('status.robot.online'), value: 'online' },
  { label: t('status.robot.offline'), value: 'offline' },
  { label: t('status.robot.busy'), value: 'busy' },
  { label: t('status.robot.maintenance'), value: 'maintenance' },
  { label: t('status.robot.error'), value: 'error' }
])

const modelOptions = computed<SelectOption[]>(() =>
  models.value.map((m) => ({ label: m.model_name, value: m.model_id }))
)

const orgOptions = computed<SelectOption[]>(() =>
  orgs.value.map((o) => ({ label: o.org_name, value: o.id }))
)

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadRobots(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listRobots({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      name: filters.name || undefined,
      status: filters.status || undefined
    })
    robots.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

async function loadOrgs(): Promise<void> {
  try {
    orgs.value = await listOrgs()
  } catch {
    // Optional dependency; ignore failure
  }
}

async function loadModels(): Promise<void> {
  try {
    const result = await listRobotModels({ page_number: 1, page_size: 100 })
    models.value = result.items
  } catch {
    // Optional dependency; ignore failure
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadRobots()
}

function handleReset(): void {
  filters.name = ''
  filters.status = ''
  pagination.value.page_number = 1
  loadRobots()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadRobots()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadRobots()
}

// ---- Create / Edit ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => [
  { key: 'name', label: t('robot.name'), type: 'text', required: true },
  {
    key: 'model_id',
    label: t('robot.model'),
    type: 'select',
    required: true,
    options: modelOptions.value,
    placeholder: t('robot.select_model')
  },
  { key: 'serial_number', label: t('robot.serial_number'), type: 'text', required: true },
  {
    key: 'org_id',
    label: t('robot.org'),
    type: 'select',
    required: true,
    options: orgOptions.value,
    placeholder: t('robot.select_org')
  },
  {
    key: 'capabilities',
    label: t('robot.capabilities'),
    type: 'textarea',
    placeholder: t('robot.capabilities_hint')
  }
])

function capabilitiesToText(capabilities: string[]): string {
  return capabilities.join('\n')
}

function textToCapabilities(text: unknown): string[] {
  if (!text) return []
  return String(text)
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
}

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModel.capabilities = ''
  formVisible.value = true
}

function openEdit(row: Robot): void {
  formMode.value = 'edit'
  formModel.id = row.id
  formModel.name = row.name
  formModel.model_id = row.model_id
  formModel.serial_number = row.serial_number
  formModel.org_id = row.org_id
  formModel.capabilities = capabilitiesToText(row.capabilities ?? [])
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'create') {
      await createRobot({
        name: String(data.name),
        model_id: String(data.model_id),
        serial_number: String(data.serial_number),
        org_id: String(data.org_id),
        capabilities: textToCapabilities(data.capabilities)
      })
    } else {
      await updateRobot(String(formModel.id), {
        name: String(data.name),
        model_id: String(data.model_id),
        serial_number: String(data.serial_number),
        org_id: String(data.org_id)
      })
      const caps = textToCapabilities(data.capabilities)
      await updateRobotCapabilities(String(formModel.id), caps)
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRobots()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Status update ----

const statusModalVisible = ref(false)
const statusTarget = ref<Robot | null>(null)
const statusValue = ref('')

function openStatusUpdate(row: Robot): void {
  statusTarget.value = row
  statusValue.value = row.status
  statusModalVisible.value = true
}

async function handleStatusConfirm(): Promise<void> {
  if (!statusTarget.value) return
  try {
    await updateRobotStatus(statusTarget.value.id, statusValue.value)
    statusModalVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRobots()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Delete ----

const deleteModalVisible = ref(false)
const deleteTarget = ref<Robot | null>(null)

function openDelete(row: Robot): void {
  deleteTarget.value = row
  deleteModalVisible.value = true
}

async function handleDeleteConfirm(): Promise<void> {
  if (!deleteTarget.value) return
  try {
    await deleteRobot(deleteTarget.value.id)
    deleteModalVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRobots()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Capabilities view ----

const capsModalVisible = ref(false)
const capsTarget = ref<Robot | null>(null)
const capsList = ref<string[]>([])
const capsLoading = ref(false)

async function openCapabilities(row: Robot): Promise<void> {
  capsTarget.value = row
  capsModalVisible.value = true
  capsLoading.value = true
  errorMsg.value = ''
  try {
    capsList.value = await getRobotCapabilities(row.id)
  } catch (err) {
    capsList.value = row.capabilities ?? []
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    capsLoading.value = false
  }
}

onMounted(() => {
  loadOrgs()
  loadModels()
  loadRobots()
})
</script>

<template>
  <div class="robot-management">
    <h1 class="page-title">{{ t('robot.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.name"
        class="filter-input"
        type="text"
        :placeholder="t('robot.name')"
        @keyup.enter="handleSearch"
      />
      <select v-model="filters.status" class="filter-select">
        <option v-for="opt in statusFilterOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'platform.robot.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="robots"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #cell-name="{ row }">
        <button class="btn-link" @click="router.push(`/robots/${(row as unknown as Robot).id}`)">
          {{ (row as unknown as Robot).name }}
        </button>
      </template>
      <template #cell-status="{ row }">
        <StatusTag :status="row.status as string" type="robot" />
      </template>
      <template #actions="{ row }">
        <div class="action-buttons">
          <button class="btn-link" @click="router.push(`/robots/${(row as unknown as Robot).id}`)">
            {{ t('common.view_detail') }}
          </button>
          <button v-permission="'platform.robot.manage'" class="btn-link" @click="openEdit(row as unknown as Robot)">
            {{ t('common.edit') }}
          </button>
          <button v-permission="'platform.robot.manage'" class="btn-link" @click="openStatusUpdate(row as unknown as Robot)">
            {{ t('robot.update_status') }}
          </button>
          <button class="btn-link" @click="openCapabilities(row as unknown as Robot)">
            {{ t('robot.view_capabilities') }}
          </button>
          <button v-permission="'platform.robot.manage'" class="btn-link btn-danger" @click="openDelete(row as unknown as Robot)">
            {{ t('common.delete') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Create / Edit robot -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('robot.create_title') : t('robot.edit_title')"
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

    <!-- Status update -->
    <ModalDialog
      :visible="statusModalVisible"
      :title="t('robot.update_status')"
      :width="400"
      @close="statusModalVisible = false"
    >
      <div class="form-field">
        <label class="form-label">{{ t('common.status') }}</label>
        <select v-model="statusValue" class="form-input">
          <option v-for="opt in statusActionOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>
      <template #footer>
        <button class="btn btn-primary" @click="handleStatusConfirm">{{ t('common.confirm') }}</button>
        <button class="btn btn-secondary" @click="statusModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Capabilities view -->
    <ModalDialog
      :visible="capsModalVisible"
      :title="t('robot.capabilities_title')"
      :width="440"
      @close="capsModalVisible = false"
    >
      <p v-if="capsLoading" class="loading-text">{{ t('common.loading') }}</p>
      <template v-else>
        <p v-if="capsTarget" class="caps-name">{{ capsTarget.name }}</p>
        <div v-if="capsList.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
        <ul v-else class="caps-list">
          <li v-for="cap in capsList" :key="cap" class="caps-item">{{ cap }}</li>
        </ul>
      </template>
      <template #footer>
        <button class="btn btn-secondary" @click="capsModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Delete confirm -->
    <ModalDialog
      :visible="deleteModalVisible"
      :title="t('common.delete')"
      :width="400"
      @close="deleteModalVisible = false"
    >
      <p class="confirm-text">{{ t('common.confirm_delete') }}</p>
      <template #footer>
        <button class="btn btn-danger" @click="handleDeleteConfirm">{{ t('common.delete') }}</button>
        <button class="btn btn-secondary" @click="deleteModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.robot-management {
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
  min-width: 14rem;
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

.btn-link.btn-danger {
  color: #dc2626;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
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

.form-input:focus {
  border-color: #2563eb;
}

.confirm-text {
  font-size: 0.875rem;
  color: #334155;
}

.loading-text {
  color: #64748b;
  font-size: 0.875rem;
}

.caps-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 0.5rem;
}

.caps-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.caps-item {
  padding: 0.375rem 0.625rem;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  font-size: 0.8125rem;
  color: #1e293b;
  font-family: monospace;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  font-size: 0.875rem;
  padding: 1.5rem;
}
</style>
