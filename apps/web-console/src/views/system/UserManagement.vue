<script setup lang="ts">
// Function: User management view with table, filters, create/edit and role assignment
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listUsers,
  createUser,
  updateUser,
  updateUserStatus,
  getUserRoles,
  assignRoles
} from '@/api/user'
import { listOrgs } from '@/api/org'
import { listRoles } from '@/api/role'
import type {
  User,
  Org,
  Role,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const users = ref<User[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  keyword: '',
  org_id: '',
  status: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const orgs = ref<Org[]>([])
const roles = ref<Role[]>([])

const columns = computed<DataTableColumn[]>(() => [
  { key: 'username', title: t('common.username'), sortable: true },
  { key: 'display_name', title: t('user.display_name') },
  { key: 'email', title: t('user.email') },
  { key: 'org_name', title: t('user.org') },
  { key: 'status', title: t('common.status') },
  { key: 'created_at', title: t('common.created_at'), sortable: true }
])

const orgOptions = computed<SelectOption[]>(() =>
  orgs.value.map((o) => ({ label: o.org_name, value: o.id }))
)

const statusOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('status.enable-disable.enabled'), value: 'enabled' },
  { label: t('status.enable-disable.disabled'), value: 'disabled' }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadUsers(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listUsers({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      keyword: filters.keyword || undefined,
      org_id: filters.org_id || undefined,
      status: filters.status || undefined
    })
    users.value = result.items
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
    // Orgs optional for filter; ignore failure
  }
}

async function loadRoles(): Promise<void> {
  try {
    const result = await listRoles({
      page_number: 1,
      page_size: 200
    })
    roles.value = result.items
  } catch {
    // Roles optional; ignore failure
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadUsers()
}

function handleReset(): void {
  filters.keyword = ''
  filters.org_id = ''
  filters.status = ''
  pagination.value.page_number = 1
  loadUsers()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadUsers()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadUsers()
}

// ---- Create/Edit modal ----

const formModalVisible = ref(false)
const formModalMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})
const formSaving = ref(false)

const formFields = computed<FormField[]>(() => {
  const fields: FormField[] = [
    { key: 'username', label: t('common.username'), type: 'text', required: true },
    { key: 'display_name', label: t('user.display_name'), type: 'text', required: true },
    { key: 'email', label: t('user.email'), type: 'email', required: true },
    { key: 'phone', label: t('user.phone'), type: 'text' },
    {
      key: 'org_id',
      label: t('user.org'),
      type: 'select',
      required: true,
      options: orgOptions.value,
      placeholder: t('user.select_org')
    },
    {
      key: 'role_id',
      label: t('user.role'),
      type: 'select',
      options: roles.value.map((r) => ({ label: r.role_name, value: r.id })),
      placeholder: t('user.select_role')
    }
  ]
  if (formModalMode.value === 'create') {
    fields.splice(4, 0, {
      key: 'password',
      label: t('common.password'),
      type: 'password',
      required: true
    })
  }
  return fields
})

function openCreate(): void {
  formModalMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModalVisible.value = true
}

function openEdit(row: User): void {
  formModalMode.value = 'edit'
  formModel.id = row.id
  formModel.username = row.username
  formModel.display_name = row.display_name
  formModel.email = row.email
  formModel.phone = row.phone
  formModel.org_id = row.org_id
  formModalVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  formSaving.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formModalMode.value === 'create') {
      await createUser({
        username: String(data.username),
        display_name: String(data.display_name),
        email: String(data.email),
        phone: String(data.phone ?? ''),
        password: String(data.password),
        org_id: String(data.org_id),
        role_ids: data.role_id ? [String(data.role_id)] : []
      })
    } else {
      await updateUser(String(formModel.id), {
        display_name: String(data.display_name),
        email: String(data.email),
        phone: String(data.phone ?? ''),
        org_id: String(data.org_id)
      })
    }
    formModalVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadUsers()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    formSaving.value = false
  }
}

// ---- Enable/Disable toggle ----

const confirmVisible = ref(false)
const pendingToggle = ref<User | null>(null)

function openToggle(row: User): void {
  pendingToggle.value = row
  confirmVisible.value = true
}

async function handleToggleConfirm(): Promise<void> {
  if (!pendingToggle.value) return
  const next = pendingToggle.value.status === 'enabled' ? 'disabled' : 'enabled'
  try {
    await updateUserStatus(pendingToggle.value.id, next)
    confirmVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadUsers()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Assign roles modal ----

const rolesModalVisible = ref(false)
const rolesTarget = ref<User | null>(null)
const roleOptions = ref<Role[]>([])
const selectedRoleIds = ref<string[]>([])

async function openAssignRoles(row: User): Promise<void> {
  rolesTarget.value = row
  roleOptions.value = roles.value
  errorMsg.value = ''
  try {
    const assigned = await getUserRoles(row.id)
    selectedRoleIds.value = assigned.map((r) => r.id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
    selectedRoleIds.value = []
  }
  rolesModalVisible.value = true
}

function toggleRole(roleId: string): void {
  const idx = selectedRoleIds.value.indexOf(roleId)
  if (idx >= 0) {
    selectedRoleIds.value.splice(idx, 1)
  } else {
    selectedRoleIds.value.push(roleId)
  }
}

async function handleAssignRolesConfirm(): Promise<void> {
  if (!rolesTarget.value) return
  try {
    await assignRoles(rolesTarget.value.id, selectedRoleIds.value)
    rolesModalVisible.value = false
    successMsg.value = t('common.operation_success')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadOrgs()
  loadRoles()
  loadUsers()
})
</script>

<template>
  <div class="user-management">
    <h1 class="page-title">{{ t('user.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('user.keyword_placeholder')"
        @keyup.enter="handleSearch"
      />
      <select v-model="filters.org_id" class="filter-select">
        <option value="">{{ t('common.all') }}</option>
        <option v-for="org in orgs" :key="org.id" :value="org.id">{{ org.org_name }}</option>
      </select>
      <select v-model="filters.status" class="filter-select">
        <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'platform.user.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="users"
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
          <button
            v-permission="'platform.user.manage'"
            class="btn-link"
            @click="openEdit(row as unknown as User)"
          >
            {{ t('common.edit') }}
          </button>
          <button
            v-permission="'platform.user.manage'"
            class="btn-link"
            @click="openToggle(row as unknown as User)"
          >
            {{ (row as unknown as User).status === 'enabled' ? t('common.disable') : t('common.enable') }}
          </button>
          <button
            v-permission="'platform.user.manage'"
            class="btn-link"
            @click="openAssignRoles(row as unknown as User)"
          >
            {{ t('common.assign_roles') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Create / Edit user -->
    <ModalDialog
      :visible="formModalVisible"
      :title="formModalMode === 'create' ? t('user.create_title') : t('user.edit_title')"
      :width="520"
      @close="formModalVisible = false"
    >
      <FormBuilder
        :fields="formFields"
        :model-value="formModel"
        @submit="handleFormSubmit"
        @cancel="formModalVisible = false"
      />
      <template #footer>
        <span v-if="formSaving" class="saving-hint">{{ t('common.loading') }}</span>
      </template>
    </ModalDialog>

    <!-- Toggle status confirm -->
    <ModalDialog
      :visible="confirmVisible"
      :title="t('common.enable_disable')"
      :width="400"
      @close="confirmVisible = false"
    >
      <p class="confirm-text">{{ t('common.confirm_toggle') }}</p>
      <template #footer>
        <button class="btn btn-primary" @click="handleToggleConfirm">{{ t('common.confirm') }}</button>
        <button class="btn btn-secondary" @click="confirmVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Assign roles -->
    <ModalDialog
      :visible="rolesModalVisible"
      :title="t('user.assign_roles_title')"
      :width="480"
      @close="rolesModalVisible = false"
    >
      <div class="role-check-list">
        <label v-for="role in roleOptions" :key="role.id" class="role-check-item">
          <input
            type="checkbox"
            :checked="selectedRoleIds.includes(role.id)"
            @change="toggleRole(role.id)"
          />
          <span class="role-label">{{ role.role_name }}</span>
          <span class="role-code">{{ role.role_code }}</span>
        </label>
      </div>
      <template #footer>
        <button class="btn btn-primary" @click="handleAssignRolesConfirm">{{ t('common.confirm') }}</button>
        <button class="btn btn-secondary" @click="rolesModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.user-management {
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

.saving-hint {
  font-size: 0.8125rem;
  color: #64748b;
}

.confirm-text {
  font-size: 0.875rem;
  color: #334155;
}

.role-check-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.role-check-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  cursor: pointer;
}

.role-check-item:hover {
  background-color: #f8fafc;
}

.role-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #1e293b;
}

.role-code {
  font-size: 0.75rem;
  color: #64748b;
  margin-left: auto;
}
</style>
