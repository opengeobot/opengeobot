<!--
  Function: Backup, restore and disaster recovery drill management page
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
import { listBackups, triggerBackup, restore, listDrills, createDrill } from '@/api/recovery'
import type {
  DataTableColumn,
  DataTablePagination,
  FormField,
  BackupRecord,
  DrillRecord,
  BackupType,
  DrillType,
  BackupListParams,
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

// ---- Backup list ----
const backupRows = ref<BackupRecord[]>([])
const backupLoading = ref(false)
const backupPagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const backupColumns = computed<DataTableColumn[]>(() => [
  { key: 'type', title: t('recovery.backup_type') },
  { key: 'file_path', title: t('recovery.file_path') },
  { key: 'file_size', title: t('recovery.file_size') },
  { key: 'status', title: t('recovery.backup_status') },
  { key: 'started_at', title: t('recovery.started_at') },
  { key: 'completed_at', title: t('recovery.completed_at') }
])

async function loadBackups() {
  backupLoading.value = true
  errorMsg.value = ''
  try {
    const params: BackupListParams = {
      page_number: backupPagination.value.page_number,
      page_size: backupPagination.value.page_size
    }
    const result = await listBackups(params)
    backupRows.value = result.items
    backupPagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    backupLoading.value = false
  }
}

function onBackupPageChange(page: number) {
  backupPagination.value.page_number = page
  loadBackups()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

// ---- Trigger backup ----
const backupModalVisible = ref(false)
const backupModel = reactive<Record<string, unknown>>({})

const backupFields = computed<FormField[]>(() => [
  {
    key: 'type',
    label: t('recovery.backup_type'),
    type: 'select',
    required: true,
    options: [
      { label: 'DATABASE', value: 'DATABASE' },
      { label: 'MINIO', value: 'MINIO' }
    ]
  }
])

function openBackupModal() {
  Object.assign(backupModel, { type: 'DATABASE' })
  backupModalVisible.value = true
}

async function handleBackupSubmit(data: Record<string, unknown>) {
  errorMsg.value = ''
  try {
    await triggerBackup({ type: data.type as BackupType })
    backupModalVisible.value = false
    loadBackups()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Restore ----
async function handleRestore(row: BackupRecord) {
  if (!confirm(t('recovery.restore_confirm'))) return
  errorMsg.value = ''
  try {
    await restore({ backup_id: row.backup_id })
    loadBackups()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Drill list ----
const drillRows = ref<DrillRecord[]>([])
const drillLoading = ref(false)
const drillPagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const drillColumns = computed<DataTableColumn[]>(() => [
  { key: 'type', title: t('recovery.drill_type') },
  { key: 'result', title: t('recovery.drill_result') },
  { key: 'notes', title: t('recovery.notes') },
  { key: 'executed_at', title: t('recovery.executed_at') },
  { key: 'executed_by', title: t('recovery.executed_by') }
])

async function loadDrills() {
  drillLoading.value = true
  try {
    const params: BackupListParams = {
      page_number: drillPagination.value.page_number,
      page_size: drillPagination.value.page_size
    }
    const result = await listDrills(params)
    drillRows.value = result.items
    drillPagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    drillLoading.value = false
  }
}

function onDrillPageChange(page: number) {
  drillPagination.value.page_number = page
  loadDrills()
}

// ---- Create drill ----
const drillModalVisible = ref(false)
const drillModel = reactive<Record<string, unknown>>({})

const drillFields = computed<FormField[]>(() => [
  {
    key: 'type',
    label: t('recovery.drill_type'),
    type: 'select',
    required: true,
    options: [
      { label: 'BACKUP_VERIFY', value: 'BACKUP_VERIFY' },
      { label: 'RESTORE_SIMULATION', value: 'RESTORE_SIMULATION' },
      { label: 'FAILOVER', value: 'FAILOVER' }
    ]
  },
  { key: 'notes', label: t('recovery.notes'), type: 'textarea' }
])

function openDrillModal() {
  Object.assign(drillModel, { type: 'BACKUP_VERIFY', notes: '' })
  drillModalVisible.value = true
}

async function handleDrillSubmit(data: Record<string, unknown>) {
  errorMsg.value = ''
  try {
    await createDrill({
      type: data.type as DrillType,
      notes: data.notes ? String(data.notes) : undefined
    })
    drillModalVisible.value = false
    loadDrills()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadBackups()
  loadDrills()
})
</script>

<template>
  <div class="recovery-management">
    <div class="page-header">
      <h2>{{ t('recovery.title') }}</h2>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>

    <section class="card">
      <div class="section-toolbar">
        <h3>{{ t('recovery.backups') }}</h3>
        <button class="btn btn-primary" @click="openBackupModal">{{ t('recovery.trigger_backup') }}</button>
      </div>
      <DataTable
        :columns="backupColumns"
        :data="backupRows"
        :loading="backupLoading"
        :pagination="backupPagination"
        @page-change="onBackupPageChange"
      >
        <template #cell-file_size="{ row }">
          {{ formatSize(Number(row.file_size)) }}
        </template>
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="recovery" />
        </template>
        <template #actions="{ row }">
          <button
            v-if="row.status === 'COMPLETED'"
            class="btn-link"
            @click="handleRestore(row as unknown as BackupRecord)"
          >
            {{ t('recovery.restore') }}
          </button>
        </template>
      </DataTable>
    </section>

    <section class="card">
      <div class="section-toolbar">
        <h3>{{ t('recovery.drills') }}</h3>
        <button class="btn btn-primary" @click="openDrillModal">{{ t('common.create') }}</button>
      </div>
      <DataTable
        :columns="drillColumns"
        :data="drillRows"
        :loading="drillLoading"
        :pagination="drillPagination"
        @page-change="onDrillPageChange"
      >
        <template #cell-result="{ row }">
          <StatusTag :status="row.result as string" type="recovery" />
        </template>
      </DataTable>
    </section>

    <ModalDialog :visible="backupModalVisible" :title="t('recovery.trigger_backup')" :width="420" @close="backupModalVisible = false">
      <FormBuilder :fields="backupFields" :model-value="backupModel" @submit="handleBackupSubmit" @cancel="backupModalVisible = false" />
    </ModalDialog>

    <ModalDialog :visible="drillModalVisible" :title="t('recovery.create_drill_title')" :width="480" @close="drillModalVisible = false">
      <FormBuilder :fields="drillFields" :model-value="drillModel" @submit="handleDrillSubmit" @cancel="drillModalVisible = false" />
    </ModalDialog>
  </div>
</template>

<style scoped>
.recovery-management {
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
