<!--
  Function: Edge gateway list — register, activate, revoke and navigate to detail
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listEdgeGateways,
  registerEdgeGateway,
  activateEdgeGateway,
  revokeEdgeGateway
} from '@/api/edge'
import { listOrgs } from '@/api/org'
import type {
  EdgeGateway,
  Org,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const rows = ref<EdgeGateway[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const orgs = ref<Org[]>([])
const filters = reactive({ status: '', keyword: '' })
const pagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const hasPermission = computed(() => authStore.permissions.includes('edge.gateway.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('edge.name'), sortable: true },
  { key: 'gateway_id', title: t('edge.gateway_id') },
  { key: 'status', title: t('common.status') },
  { key: 'runtime_version', title: t('edge.runtime_version') },
  { key: 'bound_robot_id', title: t('edge.bound_robot') },
  { key: 'last_heartbeat_at', title: t('edge.last_heartbeat') }
])

const statusOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('edge.status_pending'), value: 'PENDING' },
  { label: t('edge.status_active'), value: 'ACTIVE' },
  { label: t('edge.status_revoked'), value: 'REVOKED' }
])

const orgOptions = computed<SelectOption[]>(() =>
  orgs.value.map((o) => ({ label: o.org_name, value: o.id }))
)

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
  forbidden.value = false
  try {
    const result = await listEdgeGateways({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      status: filters.status || undefined
    })
    rows.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) {
      forbidden.value = true
    } else {
      errorMsg.value = resolveError(problem)
    }
  } finally {
    loading.value = false
  }
}

async function loadOrgs(): Promise<void> {
  try {
    orgs.value = await listOrgs()
  } catch {
    // optional
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadRows()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadRows()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadRows()
}

const formVisible = ref(false)
const formModel = reactive<Record<string, unknown>>({})
const formFields = computed<FormField[]>(() => [
  { key: 'name', label: t('edge.name'), type: 'text', required: true },
  {
    key: 'org_id',
    label: t('edge.org'),
    type: 'select',
    required: true,
    options: orgOptions.value,
    placeholder: t('common.select')
  },
  { key: 'bound_robot_id', label: t('edge.bound_robot'), type: 'text' },
  { key: 'runtime_version', label: t('edge.runtime_version'), type: 'text' }
])

function openCreate(): void {
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

async function handleCreate(values: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  try {
    await registerEdgeGateway({
      name: String(values.name),
      org_id: String(values.org_id),
      bound_robot_id: values.bound_robot_id ? String(values.bound_robot_id) : undefined,
      runtime_version: values.runtime_version ? String(values.runtime_version) : undefined
    })
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleActivate(row: EdgeGateway): Promise<void> {
  try {
    await activateEdgeGateway(row.gateway_id)
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleRevoke(row: EdgeGateway): Promise<void> {
  try {
    await revokeEdgeGateway(row.gateway_id)
    successMsg.value = t('common.operation_success')
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadOrgs()
  if (hasPermission.value) {
    loadRows()
  } else {
    forbidden.value = true
  }
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('edge.title') }}</h1>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('edge.forbidden') }}</div>

    <template v-else>
      <div class="toolbar">
        <select v-model="filters.status" class="filter-select">
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
        <button v-permission="'edge.gateway.manage'" class="btn btn-primary" @click="openCreate">
          {{ t('edge.register') }}
        </button>
      </div>

      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>
      <p v-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
      <p v-else-if="viewState === 'EMPTY'" class="empty-text">{{ t('common.no_data') }}</p>

      <DataTable
        v-if="viewState === 'READY' || viewState === 'ERROR'"
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        @page-change="handlePageChange"
        @size-change="handleSizeChange"
      >
        <template #cell-name="{ row }">
          <button class="btn-link" @click="router.push(`/edge-gateways/${(row as unknown as EdgeGateway).gateway_id}`)">
            {{ (row as unknown as EdgeGateway).name }}
          </button>
        </template>
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="enable-disable" />
        </template>
        <template #actions="{ row }">
          <div class="action-buttons">
            <button class="btn-link" @click="router.push(`/edge-gateways/${(row as unknown as EdgeGateway).gateway_id}`)">
              {{ t('common.view_detail') }}
            </button>
            <button
              v-if="(row as unknown as EdgeGateway).status === 'PENDING'"
              v-permission="'edge.gateway.manage'"
              class="btn-link"
              @click="handleActivate(row as unknown as EdgeGateway)"
            >
              {{ t('edge.activate') }}
            </button>
            <button
              v-if="(row as unknown as EdgeGateway).status === 'ACTIVE'"
              v-permission="'edge.gateway.manage'"
              class="btn-link btn-danger"
              @click="handleRevoke(row as unknown as EdgeGateway)"
            >
              {{ t('edge.revoke') }}
            </button>
          </div>
        </template>
      </DataTable>
    </template>

    <ModalDialog :visible="formVisible" :title="t('edge.register')" :width="480" @close="formVisible = false">
      <FormBuilder
        :fields="formFields"
        :model-value="formModel"
        @submit="handleCreate"
        @cancel="formVisible = false"
      />
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.toolbar { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.filter-select { padding: 0.5rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 0.375rem; font-size: 0.875rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.btn-link { background: transparent; border: none; color: #2563eb; cursor: pointer; font-size: 0.8125rem; padding: 0; }
.btn-danger { color: #dc2626; }
.action-buttons { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text, .empty-text { color: #64748b; font-size: 0.875rem; }
</style>
