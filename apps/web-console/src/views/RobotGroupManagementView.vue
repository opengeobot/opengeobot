<!--
  Function: Robot group CRUD and member management page
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
  listRobotGroups,
  createRobotGroup,
  updateRobotGroup,
  deleteRobotGroup,
  listRobotGroupMembers,
  addRobotGroupMember,
  removeRobotGroupMember
} from '@/api/robot'
import type {
  RobotGroup,
  FormField,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const rows = ref<RobotGroup[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const pagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const hasPermission = computed(() => authStore.permissions.includes('robot.group.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'group_name', title: t('robot_group.name'), sortable: true },
  { key: 'group_id', title: t('robot_group.id') },
  { key: 'parent_id', title: t('robot_group.parent') },
  { key: 'path', title: t('robot_group.path') },
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
    const result = await listRobotGroups({
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
const deleteTarget = ref<RobotGroup | null>(null)

const formFields = computed<FormField[]>(() => [
  { key: 'group_name', label: t('robot_group.name'), type: 'text', required: true },
  { key: 'parent_id', label: t('robot_group.parent'), type: 'text', placeholder: t('robot_group.parent_hint') },
  { key: 'description', label: t('common.description'), type: 'textarea' }
])

function openCreate(): void {
  formMode.value = 'create'
  editingId.value = ''
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

function openEdit(row: RobotGroup): void {
  formMode.value = 'edit'
  editingId.value = row.group_id
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModel.group_name = row.group_name
  formModel.description = row.description
  formVisible.value = true
}

async function handleSubmit(values: Record<string, unknown>): Promise<void> {
  try {
    if (formMode.value === 'create') {
      await createRobotGroup({
        group_name: String(values.group_name),
        parent_id: values.parent_id ? String(values.parent_id) : null,
        description: values.description ? String(values.description) : undefined
      })
    } else {
      await updateRobotGroup(editingId.value, {
        group_name: String(values.group_name),
        description: values.description ? String(values.description) : undefined
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

function openDelete(row: RobotGroup): void {
  deleteTarget.value = row
  deleteVisible.value = true
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  try {
    await deleteRobotGroup(deleteTarget.value.group_id)
    deleteVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

const membersVisible = ref(false)
const membersGroup = ref<RobotGroup | null>(null)
const memberIds = ref<string[]>([])
const memberRobotId = ref('')
const membersLoading = ref(false)

async function openMembers(row: RobotGroup): Promise<void> {
  membersGroup.value = row
  membersVisible.value = true
  membersLoading.value = true
  memberRobotId.value = ''
  try {
    const result = await listRobotGroupMembers(row.group_id)
    memberIds.value = result.robot_ids
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
    memberIds.value = []
  } finally {
    membersLoading.value = false
  }
}

async function handleAddMember(): Promise<void> {
  if (!membersGroup.value || !memberRobotId.value.trim()) return
  try {
    await addRobotGroupMember(membersGroup.value.group_id, memberRobotId.value.trim())
    memberRobotId.value = ''
    successMsg.value = t('common.operation_success')
    await openMembers(membersGroup.value)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleRemoveMember(robotId: string): Promise<void> {
  if (!membersGroup.value) return
  try {
    await removeRobotGroupMember(membersGroup.value.group_id, robotId)
    successMsg.value = t('common.operation_success')
    await openMembers(membersGroup.value)
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
    <h1 class="page-title">{{ t('robot_group.title') }}</h1>
    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('robot_group.forbidden') }}</div>
    <template v-else>
      <div class="toolbar">
        <button class="btn btn-secondary" @click="loadRows">{{ t('common.refresh') }}</button>
        <button v-permission="'robot.group.manage'" class="btn btn-primary" @click="openCreate">
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
            <button class="btn-link" @click="openMembers(row as unknown as RobotGroup)">
              {{ t('robot_group.members') }}
            </button>
            <button v-permission="'robot.group.manage'" class="btn-link" @click="openEdit(row as unknown as RobotGroup)">
              {{ t('common.edit') }}
            </button>
            <button v-permission="'robot.group.manage'" class="btn-link btn-danger" @click="openDelete(row as unknown as RobotGroup)">
              {{ t('common.delete') }}
            </button>
          </div>
        </template>
      </DataTable>
    </template>

    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('robot_group.create_title') : t('robot_group.edit_title')"
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

    <ModalDialog
      :visible="membersVisible"
      :title="t('robot_group.members_title')"
      :width="520"
      @close="membersVisible = false"
    >
      <p v-if="membersLoading" class="loading-text">{{ t('common.loading') }}</p>
      <template v-else>
        <div class="member-add">
          <input v-model="memberRobotId" class="filter-input" :placeholder="t('robot_group.robot_id')" />
          <button v-permission="'robot.group.manage'" class="btn btn-primary" @click="handleAddMember">
            {{ t('robot_group.add_member') }}
          </button>
        </div>
        <ul v-if="memberIds.length" class="member-list">
          <li v-for="id in memberIds" :key="id" class="member-item">
            <span class="mono">{{ id }}</span>
            <button v-permission="'robot.group.manage'" class="btn-link btn-danger" @click="handleRemoveMember(id)">
              {{ t('common.delete') }}
            </button>
          </li>
        </ul>
        <p v-else class="empty-text">{{ t('common.no_data') }}</p>
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
.action-buttons { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text, .empty-text { color: #64748b; }
.member-add { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
.filter-input { flex: 1; padding: 0.5rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 0.375rem; }
.member-list { list-style: none; margin: 0; padding: 0; }
.member-item { display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid #e2e8f0; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; }
</style>
