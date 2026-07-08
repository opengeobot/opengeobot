<!--
  Function: Fleet scheduling, conflict detection and failover event management page
  Time: 2026-07-05
  Author: AxeXie
-->
<script setup lang="ts">
import { onMounted, ref, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listSchedules, createSchedule, listConflicts, resolveConflict, listFailovers, triggerFailover } from '@/api/fleet'
import type {
  DataTableColumn,
  DataTablePagination,
  FormField,
  FleetSchedule,
  ConflictRecord,
  FailoverEvent,
  ConflictResolution,
  FleetScheduleListParams,
  ConflictListParams,
  FailoverListParams,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const errorMsg = ref('')

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

// ---- Schedule list ----
const scheduleRows = ref<FleetSchedule[]>([])
const scheduleLoading = ref(false)
const schedulePagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const scheduleColumns = computed<DataTableColumn[]>(() => [
  { key: 'mission_id', title: t('fleet.mission') },
  { key: 'robot_id', title: t('fleet.robot') },
  { key: 'planned_start', title: t('fleet.planned_start') },
  { key: 'planned_end', title: t('fleet.planned_end') },
  { key: 'priority', title: t('fleet.priority') },
  { key: 'status', title: t('common.status') }
])

async function loadSchedules() {
  scheduleLoading.value = true
  errorMsg.value = ''
  try {
    const params: FleetScheduleListParams = {
      page_number: schedulePagination.value.page_number,
      page_size: schedulePagination.value.page_size
    }
    const result = await listSchedules(params)
    scheduleRows.value = result.items
    schedulePagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    scheduleLoading.value = false
  }
}

function onSchedulePageChange(page: number) {
  schedulePagination.value.page_number = page
  loadSchedules()
}

function onScheduleSizeChange(size: number) {
  schedulePagination.value.page_size = size
  schedulePagination.value.page_number = 1
  loadSchedules()
}

// ---- Create schedule ----
const createModalVisible = ref(false)
const createModel = reactive<Record<string, unknown>>({})

const createFields = computed<FormField[]>(() => [
  { key: 'mission_id', label: t('fleet.mission'), type: 'text', placeholder: t('fleet.select_mission'), required: true },
  { key: 'robot_id', label: t('fleet.robot'), type: 'text', placeholder: t('fleet.select_robot'), required: true },
  { key: 'planned_start', label: t('fleet.planned_start'), type: 'text', required: true },
  { key: 'planned_end', label: t('fleet.planned_end'), type: 'text', required: true },
  {
    key: 'priority',
    label: t('fleet.priority'),
    type: 'select',
    options: [
      { label: t('fleet.priority_low'), value: 'LOW' },
      { label: t('fleet.priority_normal'), value: 'NORMAL' },
      { label: t('fleet.priority_high'), value: 'HIGH' },
      { label: t('fleet.priority_urgent'), value: 'URGENT' }
    ]
  }
])

function openCreateModal() {
  Object.assign(createModel, { mission_id: '', robot_id: '', planned_start: '', planned_end: '', priority: 'NORMAL' })
  createModalVisible.value = true
}

async function handleCreateSubmit(data: Record<string, unknown>) {
  errorMsg.value = ''
  try {
    await createSchedule({
      mission_id: String(data.mission_id),
      robot_id: String(data.robot_id),
      planned_start: String(data.planned_start),
      planned_end: String(data.planned_end),
      priority: (data.priority as FleetSchedule['priority']) || 'NORMAL'
    })
    createModalVisible.value = false
    loadSchedules()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Conflict list ----
const conflictRows = ref<ConflictRecord[]>([])
const conflictLoading = ref(false)
const conflictPagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const conflictColumns = computed<DataTableColumn[]>(() => [
  { key: 'conflict_type', title: t('fleet.conflict_type') },
  { key: 'status', title: t('fleet.conflict_status') },
  { key: 'description', title: t('alarm.message') },
  { key: 'detected_at', title: t('fleet.detected_at') },
  { key: 'resolved_at', title: t('fleet.resolved_at') }
])

async function loadConflicts() {
  conflictLoading.value = true
  try {
    const params: ConflictListParams = {
      page_number: conflictPagination.value.page_number,
      page_size: conflictPagination.value.page_size
    }
    const result = await listConflicts(params)
    conflictRows.value = result.items
    conflictPagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    conflictLoading.value = false
  }
}

function onConflictPageChange(page: number) {
  conflictPagination.value.page_number = page
  loadConflicts()
}

// ---- Resolve conflict ----
const resolveModalVisible = ref(false)
const resolveTarget = ref<ConflictRecord | null>(null)
const resolveModel = reactive<Record<string, unknown>>({})

const resolveFields = computed<FormField[]>(() => [
  {
    key: 'resolution',
    label: t('fleet.resolution'),
    type: 'select',
    options: [
      { label: t('fleet.resolution_reorder'), value: 'REORDER' },
      { label: t('fleet.resolution_reassign'), value: 'REASSIGN' },
      { label: t('fleet.resolution_cancel'), value: 'CANCEL' }
    ],
    required: true
  },
  { key: 'target_robot_id', label: t('fleet.target_robot'), type: 'text' }
])

function openResolveModal(row: ConflictRecord) {
  resolveTarget.value = row
  Object.assign(resolveModel, { resolution: 'REORDER', target_robot_id: '' })
  resolveModalVisible.value = true
}

async function handleResolveSubmit(data: Record<string, unknown>) {
  if (!resolveTarget.value) return
  errorMsg.value = ''
  try {
    await resolveConflict(resolveTarget.value.conflict_id, {
      resolution: data.resolution as ConflictResolution,
      target_robot_id: data.target_robot_id ? String(data.target_robot_id) : undefined
    })
    resolveModalVisible.value = false
    loadConflicts()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Failover list ----
const failoverRows = ref<FailoverEvent[]>([])
const failoverLoading = ref(false)
const failoverPagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const failoverColumns = computed<DataTableColumn[]>(() => [
  { key: 'robot_id', title: t('fleet.robot') },
  { key: 'mission_id', title: t('fleet.mission') },
  { key: 'reason', title: t('fleet.reason') },
  { key: 'target_robot_id', title: t('fleet.target_robot') },
  { key: 'status', title: t('common.status') },
  { key: 'occurred_at', title: t('fleet.occurred_at') }
])

async function loadFailovers() {
  failoverLoading.value = true
  try {
    const params: FailoverListParams = {
      page_number: failoverPagination.value.page_number,
      page_size: failoverPagination.value.page_size
    }
    const result = await listFailovers(params)
    failoverRows.value = result.items
    failoverPagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    failoverLoading.value = false
  }
}

function onFailoverPageChange(page: number) {
  failoverPagination.value.page_number = page
  loadFailovers()
}

// ---- Trigger failover ----
const failoverModalVisible = ref(false)
const failoverModel = reactive<Record<string, unknown>>({})

const failoverFields = computed<FormField[]>(() => [
  { key: 'robot_id', label: t('fleet.robot'), type: 'text', required: true },
  { key: 'mission_id', label: t('fleet.mission'), type: 'text', required: true },
  { key: 'reason', label: t('fleet.reason'), type: 'textarea', required: true },
  { key: 'target_robot_id', label: t('fleet.target_robot'), type: 'text' }
])

function openFailoverModal() {
  Object.assign(failoverModel, { robot_id: '', mission_id: '', reason: '', target_robot_id: '' })
  failoverModalVisible.value = true
}

async function handleFailoverSubmit(data: Record<string, unknown>) {
  errorMsg.value = ''
  try {
    await triggerFailover({
      robot_id: String(data.robot_id),
      mission_id: String(data.mission_id),
      reason: String(data.reason),
      target_robot_id: data.target_robot_id ? String(data.target_robot_id) : undefined
    })
    failoverModalVisible.value = false
    loadFailovers()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadSchedules()
  loadConflicts()
  loadFailovers()
})
</script>

<template>
  <div class="fleet-management">
    <div class="page-header">
      <h2>{{ t('fleet.title') }}</h2>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>

    <section class="card">
      <div class="section-toolbar">
        <h3>{{ t('fleet.schedules_title') }}</h3>
        <button class="btn btn-primary" @click="openCreateModal">{{ t('common.create') }}</button>
      </div>
      <DataTable
        :columns="scheduleColumns"
        :data="scheduleRows"
        :loading="scheduleLoading"
        :pagination="schedulePagination"
        @page-change="onSchedulePageChange"
        @size-change="onScheduleSizeChange"
      >
        <template #cell-priority="{ row }">
          {{ t(`fleet.priority_${String(row.priority).toLowerCase()}`) }}
        </template>
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="fleet" />
        </template>
      </DataTable>
    </section>

    <section class="card">
      <h3>{{ t('fleet.conflicts_title') }}</h3>
      <DataTable
        :columns="conflictColumns"
        :data="conflictRows"
        :loading="conflictLoading"
        :pagination="conflictPagination"
        @page-change="onConflictPageChange"
      >
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="fleet" />
        </template>
        <template #actions="{ row }">
          <button v-if="row.status === 'OPEN'" class="btn-link" @click="openResolveModal(row as unknown as ConflictRecord)">
            {{ t('fleet.resolve') }}
          </button>
        </template>
      </DataTable>
    </section>

    <section class="card">
      <div class="section-toolbar">
        <h3>{{ t('fleet.failovers_title') }}</h3>
        <button class="btn btn-primary" @click="openFailoverModal">{{ t('fleet.trigger_failover') }}</button>
      </div>
      <DataTable
        :columns="failoverColumns"
        :data="failoverRows"
        :loading="failoverLoading"
        :pagination="failoverPagination"
        @page-change="onFailoverPageChange"
      >
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="fleet" />
        </template>
      </DataTable>
    </section>

    <ModalDialog :visible="createModalVisible" :title="t('fleet.create_title')" :width="520" @close="createModalVisible = false">
      <FormBuilder :fields="createFields" :model-value="createModel" @submit="handleCreateSubmit" @cancel="createModalVisible = false" />
    </ModalDialog>

    <ModalDialog :visible="resolveModalVisible" :title="t('fleet.resolve')" :width="480" @close="resolveModalVisible = false">
      <FormBuilder :fields="resolveFields" :model-value="resolveModel" @submit="handleResolveSubmit" @cancel="resolveModalVisible = false" />
    </ModalDialog>

    <ModalDialog :visible="failoverModalVisible" :title="t('fleet.trigger_failover')" :width="520" @close="failoverModalVisible = false">
      <FormBuilder :fields="failoverFields" :model-value="failoverModel" @submit="handleFailoverSubmit" @cancel="failoverModalVisible = false" />
    </ModalDialog>
  </div>
</template>

<style scoped>
.fleet-management {
  padding: 1rem;
}
.page-header {
  margin-bottom: 1rem;
}
.page-header h2 {
  margin: 0;
}
.card {
  background: var(--color-surface, #fff);
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}
.section-toolbar h3 {
  margin: 0;
}
.btn {
  cursor: pointer;
  border: none;
  border-radius: 4px;
  padding: 0.4rem 0.9rem;
  font-size: 0.875rem;
}
.btn-primary {
  background: var(--color-primary, #2563eb);
  color: #fff;
}
.btn-link {
  background: none;
  border: none;
  color: var(--color-primary, #2563eb);
  cursor: pointer;
  padding: 0.2rem 0.4rem;
  font-size: 0.875rem;
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
</style>
