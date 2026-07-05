<script setup lang="ts">
// Function: Role management view with table, CRUD and permission assignment
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listRoles, createRole, updateRole, getRolePermissions, assignPermissions } from '@/api/role'
import { listPermissionsByModule } from '@/api/permission'
import type {
  Role,
  Permission,
  PermissionGroup,
  FormField,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const roles = ref<Role[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'role_name', title: t('role.role_name'), sortable: true },
  { key: 'role_code', title: t('role.role_code'), sortable: true },
  { key: 'description', title: t('common.description') },
  { key: 'status', title: t('common.status') },
  { key: 'built_in', title: t('common.built_in') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadRoles(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listRoles({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size
    })
    roles.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadRoles()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadRoles()
}

// ---- Create / Edit ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => {
  const fields: FormField[] = [
    { key: 'role_name', label: t('role.role_name'), type: 'text', required: true },
    { key: 'role_code', label: t('role.role_code'), type: 'text', required: true },
    { key: 'description', label: t('common.description'), type: 'textarea' }
  ]
  if (formMode.value === 'edit') {
    fields.push({
      key: 'status',
      label: t('common.status'),
      type: 'select',
      options: [
        { label: t('status.enable-disable.enabled'), value: 'enabled' },
        { label: t('status.enable-disable.disabled'), value: 'disabled' }
      ]
    })
  }
  return fields
})

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

function openEdit(row: Role): void {
  formMode.value = 'edit'
  formModel.id = row.id
  formModel.role_name = row.role_name
  formModel.role_code = row.role_code
  formModel.description = row.description
  formModel.status = row.status
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'create') {
      await createRole({
        role_name: String(data.role_name),
        role_code: String(data.role_code),
        description: String(data.description ?? '')
      })
    } else {
      await updateRole(String(formModel.id), {
        role_name: String(data.role_name ?? ''),
        description: String(data.description ?? ''),
        status: String(data.status ?? 'enabled')
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRoles()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Assign permissions ----

const permModalVisible = ref(false)
const permTarget = ref<Role | null>(null)
const permGroups = ref<PermissionGroup[]>([])
const selectedPermCodes = ref<string[]>([])
const permSaving = ref(false)

async function openAssignPermissions(row: Role): Promise<void> {
  permTarget.value = row
  errorMsg.value = ''
  try {
    permGroups.value = await listPermissionsByModule()
    const assigned = await getRolePermissions(row.id)
    selectedPermCodes.value = assigned.map((p) => p.permission_code)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
  permModalVisible.value = true
}

function togglePermission(code: string): void {
  const idx = selectedPermCodes.value.indexOf(code)
  if (idx >= 0) {
    selectedPermCodes.value.splice(idx, 1)
  } else {
    selectedPermCodes.value.push(code)
  }
}

function toggleModule(group: PermissionGroup, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  for (const perm of group.permissions) {
    const idx = selectedPermCodes.value.indexOf(perm.permission_code)
    if (checked && idx < 0) {
      selectedPermCodes.value.push(perm.permission_code)
    } else if (!checked && idx >= 0) {
      selectedPermCodes.value.splice(idx, 1)
    }
  }
}

function isModuleAllChecked(group: PermissionGroup): boolean {
  return group.permissions.every((p) => selectedPermCodes.value.includes(p.permission_code))
}

function isModuleSomeChecked(group: PermissionGroup): boolean {
  return group.permissions.some((p) => selectedPermCodes.value.includes(p.permission_code))
}

async function handlePermConfirm(): Promise<void> {
  if (!permTarget.value) return
  permSaving.value = true
  errorMsg.value = ''
  try {
    await assignPermissions(permTarget.value.id, selectedPermCodes.value)
    permModalVisible.value = false
    successMsg.value = t('common.operation_success')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    permSaving.value = false
  }
}

onMounted(() => {
  loadRoles()
})
</script>

<template>
  <div class="role-management">
    <h1 class="page-title">{{ t('role.title') }}</h1>

    <div class="toolbar">
      <button v-permission="'platform.role.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="roles"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #cell-status="{ row }">
        <StatusTag :status="row.status as string" type="enable-disable" />
      </template>
      <template #cell-built_in="{ value }">
        {{ value ? t('common.yes') : t('common.no') }}
      </template>
      <template #actions="{ row }">
        <div class="action-buttons">
          <button
            v-permission="'platform.role.manage'"
            class="btn-link"
            :disabled="(row as unknown as Role).built_in"
            @click="openEdit(row as unknown as Role)"
          >
            {{ t('common.edit') }}
          </button>
          <button
            v-permission="'platform.role.manage'"
            class="btn-link"
            @click="openAssignPermissions(row as unknown as Role)"
          >
            {{ t('common.assign_permissions') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Create / Edit role -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('role.create_title') : t('role.edit_title')"
      :width="480"
      @close="formVisible = false"
    >
      <FormBuilder
        :fields="formFields"
        :model-value="formModel"
        @submit="handleFormSubmit"
        @cancel="formVisible = false"
      />
    </ModalDialog>

    <!-- Assign permissions -->
    <ModalDialog
      :visible="permModalVisible"
      :title="t('role.assign_permissions_title')"
      :width="560"
      @close="permModalVisible = false"
    >
      <div class="perm-groups">
        <div v-for="group in permGroups" :key="group.module" class="perm-group">
          <label class="perm-group-header">
            <input
              type="checkbox"
              :checked="isModuleAllChecked(group)"
              :indeterminate.prop="isModuleSomeChecked(group) && !isModuleAllChecked(group)"
              @change="toggleModule(group, $event)"
            />
            <span class="group-name">{{ group.module }}</span>
          </label>
          <div class="perm-items">
            <label v-for="perm in group.permissions" :key="perm.permission_code" class="perm-item">
              <input
                type="checkbox"
                :checked="selectedPermCodes.includes(perm.permission_code)"
                @change="togglePermission(perm.permission_code)"
              />
              <span class="perm-label">{{ perm.permission_name }}</span>
              <span class="perm-code">{{ perm.permission_code }}</span>
            </label>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-primary" :disabled="permSaving" @click="handlePermConfirm">
          {{ t('common.confirm') }}
        </button>
        <button class="btn btn-secondary" @click="permModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.role-management {
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

.btn-link:hover:not(:disabled) {
  text-decoration: underline;
}

.btn-link:disabled {
  color: #cbd5e1;
  cursor: not-allowed;
}

.perm-groups {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.perm-group {
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  padding: 0.625rem;
}

.perm-group-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  font-size: 0.875rem;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.group-name {
  text-transform: uppercase;
  font-size: 0.75rem;
  letter-spacing: 0.025em;
}

.perm-items {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding-left: 1.5rem;
}

.perm-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8125rem;
  color: #334155;
  cursor: pointer;
}

.perm-code {
  font-size: 0.7rem;
  color: #94a3b8;
  margin-left: auto;
}
</style>
