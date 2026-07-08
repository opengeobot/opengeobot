<!--
  Function: OTA package upload, release campaign and deployment management page
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
import {
  listPackages,
  uploadPackage,
  listCampaigns,
  createCampaign,
  getCampaign,
  rollback
} from '@/api/ota'
import type {
  DataTableColumn,
  DataTablePagination,
  FormField,
  FirmwarePackage,
  ReleaseCampaign,
  CampaignDetail,
  PackageListParams,
  CampaignListParams,
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

// ---- Package list ----
const packageRows = ref<FirmwarePackage[]>([])
const packageLoading = ref(false)
const packagePagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const packageColumns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('ota.package_name') },
  { key: 'version', title: t('ota.version') },
  { key: 'type', title: t('ota.type') },
  { key: 'file_size', title: t('ota.file_size') },
  { key: 'created_at', title: t('common.created_at') }
])

async function loadPackages() {
  packageLoading.value = true
  errorMsg.value = ''
  try {
    const params: PackageListParams = {
      page_number: packagePagination.value.page_number,
      page_size: packagePagination.value.page_size
    }
    const result = await listPackages(params)
    packageRows.value = result.items
    packagePagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    packageLoading.value = false
  }
}

function onPackagePageChange(page: number) {
  packagePagination.value.page_number = page
  loadPackages()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

// ---- Upload package ----
const uploadModalVisible = ref(false)
const uploadLoading = ref(false)
const uploadProgress = ref(0)
const uploadFile = ref<File | null>(null)
const uploadMeta = reactive<{ name: string; version: string; type: string; description: string }>({
  name: '', version: '', type: 'FIRMWARE', description: ''
})

function openUploadModal() {
  uploadFile.value = null
  uploadProgress.value = 0
  Object.assign(uploadMeta, { name: '', version: '', type: 'FIRMWARE', description: '' })
  uploadModalVisible.value = true
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  uploadFile.value = target.files?.[0] ?? null
  if (uploadFile.value && !uploadMeta.name) {
    uploadMeta.name = uploadFile.value.name.replace(/\.[^.]+$/, '')
  }
}

async function handleUploadSubmit() {
  if (!uploadFile.value) return
  uploadLoading.value = true
  errorMsg.value = ''
  try {
    await uploadPackage(uploadFile.value, {
      name: uploadMeta.name,
      version: uploadMeta.version,
      type: uploadMeta.type as FirmwarePackage['type'],
      description: uploadMeta.description || undefined
    }, (percent) => {
      uploadProgress.value = percent
    })
    uploadModalVisible.value = false
    loadPackages()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    uploadLoading.value = false
  }
}

// ---- Campaign list ----
const campaignRows = ref<ReleaseCampaign[]>([])
const campaignLoading = ref(false)
const campaignPagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const campaignColumns = computed<DataTableColumn[]>(() => [
  { key: 'campaign_id', title: 'ID' },
  { key: 'package_id', title: t('ota.package_name') },
  { key: 'canary_percent', title: t('ota.canary_percent') },
  { key: 'status', title: t('ota.campaign_status') },
  { key: 'created_at', title: t('common.created_at') }
])

async function loadCampaigns() {
  campaignLoading.value = true
  try {
    const params: CampaignListParams = {
      page_number: campaignPagination.value.page_number,
      page_size: campaignPagination.value.page_size
    }
    const result = await listCampaigns(params)
    campaignRows.value = result.items
    campaignPagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    campaignLoading.value = false
  }
}

function onCampaignPageChange(page: number) {
  campaignPagination.value.page_number = page
  loadCampaigns()
}

// ---- Create campaign ----
const campaignModalVisible = ref(false)
const campaignModel = reactive<Record<string, unknown>>({})

const packageOptions = computed(() =>
  packageRows.value.map((p) => ({ label: `${p.name} v${p.version}`, value: p.package_id }))
)

const campaignFields = computed<FormField[]>(() => [
  {
    key: 'package_id',
    label: t('ota.packages'),
    type: 'select',
    required: true,
    options: packageOptions.value,
    placeholder: t('ota.select_package')
  },
  { key: 'canary_percent', label: t('ota.canary_percent'), type: 'number', required: true },
  { key: 'target_robots', label: t('ota.target_robots'), type: 'textarea', placeholder: t('ota.target_robots_hint'), required: true }
])

function openCampaignModal() {
  Object.assign(campaignModel, { package_id: '', canary_percent: '10', target_robots: '' })
  campaignModalVisible.value = true
}

async function handleCampaignSubmit(data: Record<string, unknown>) {
  errorMsg.value = ''
  try {
    const robots = String(data.target_robots)
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
    await createCampaign({
      package_id: String(data.package_id),
      target_robots: robots,
      canary_percent: Number(data.canary_percent)
    })
    campaignModalVisible.value = false
    loadCampaigns()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- View deployments ----
const deployModalVisible = ref(false)
const deployLoading = ref(false)
const campaignDetail = ref<CampaignDetail | null>(null)

const deployColumns: DataTableColumn[] = [
  { key: 'robot_id', title: t('fleet.robot') },
  { key: 'status', title: t('ota.deployment_status') },
  { key: 'started_at', title: t('ota.started_at') },
  { key: 'completed_at', title: t('ota.completed_at') },
  { key: 'error', title: t('ota.error_message') }
]

async function viewDeployments(row: ReleaseCampaign) {
  deployLoading.value = true
  deployModalVisible.value = true
  try {
    campaignDetail.value = await getCampaign(row.campaign_id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    deployLoading.value = false
  }
}

// ---- Rollback ----
async function handleRollback(row: ReleaseCampaign) {
  if (!confirm(t('ota.rollback_confirm'))) return
  errorMsg.value = ''
  try {
    await rollback(row.campaign_id)
    loadCampaigns()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadPackages()
  loadCampaigns()
})
</script>

<template>
  <div class="ota-management">
    <div class="page-header">
      <h2>{{ t('ota.title') }}</h2>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>

    <section class="card">
      <div class="section-toolbar">
        <h3>{{ t('ota.packages') }}</h3>
        <button class="btn btn-primary" @click="openUploadModal">{{ t('ota.upload') }}</button>
      </div>
      <DataTable
        :columns="packageColumns"
        :data="packageRows"
        :loading="packageLoading"
        :pagination="packagePagination"
        @page-change="onPackagePageChange"
      >
        <template #cell-type="{ row }">
          {{ row.type }}
        </template>
        <template #cell-file_size="{ row }">
          {{ formatSize(Number(row.file_size)) }}
        </template>
      </DataTable>
    </section>

    <section class="card">
      <div class="section-toolbar">
        <h3>{{ t('ota.campaigns') }}</h3>
        <button class="btn btn-primary" @click="openCampaignModal">{{ t('common.create') }}</button>
      </div>
      <DataTable
        :columns="campaignColumns"
        :data="campaignRows"
        :loading="campaignLoading"
        :pagination="campaignPagination"
        @page-change="onCampaignPageChange"
      >
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="ota" />
        </template>
        <template #actions="{ row }">
          <div class="action-buttons">
            <button class="btn-link" @click="viewDeployments(row as unknown as ReleaseCampaign)">
              {{ t('ota.view_deployments') }}
            </button>
            <button
              v-if="row.status === 'IN_PROGRESS' || row.status === 'COMPLETED'"
              class="btn-link"
              @click="handleRollback(row as unknown as ReleaseCampaign)"
            >
              {{ t('ota.rollback') }}
            </button>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- Upload package -->
    <ModalDialog :visible="uploadModalVisible" :title="t('ota.upload_title')" :width="520" @close="uploadModalVisible = false">
      <div class="upload-form">
        <div class="form-field">
          <label class="form-label">{{ t('ota.select_file') }}</label>
          <input type="file" class="form-input" @change="onFileChange" />
        </div>
        <div class="form-field">
          <label class="form-label">{{ t('ota.package_name') }}</label>
          <input v-model="uploadMeta.name" type="text" class="form-input" />
        </div>
        <div class="form-field">
          <label class="form-label">{{ t('ota.version') }}</label>
          <input v-model="uploadMeta.version" type="text" class="form-input" />
        </div>
        <div class="form-field">
          <label class="form-label">{{ t('ota.type') }}</label>
          <select v-model="uploadMeta.type" class="form-input">
            <option value="FIRMWARE">FIRMWARE</option>
            <option value="SKILL_BUNDLE">SKILL_BUNDLE</option>
          </select>
        </div>
        <div class="form-field">
          <label class="form-label">{{ t('common.description') }}</label>
          <textarea v-model="uploadMeta.description" class="form-input form-textarea" />
        </div>
        <div v-if="uploadLoading" class="progress-bar">
          <div class="progress-fill" :style="{ width: `${uploadProgress}%` }" />
          <span class="progress-text">{{ uploadProgress }}%</span>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-primary" :disabled="uploadLoading || !uploadFile" @click="handleUploadSubmit">
          {{ uploadLoading ? t('ota.uploading') : t('common.confirm') }}
        </button>
        <button class="btn btn-secondary" :disabled="uploadLoading" @click="uploadModalVisible = false">
          {{ t('common.cancel') }}
        </button>
      </template>
    </ModalDialog>

    <!-- Create campaign -->
    <ModalDialog :visible="campaignModalVisible" :title="t('ota.create_campaign_title')" :width="520" @close="campaignModalVisible = false">
      <FormBuilder :fields="campaignFields" :model-value="campaignModel" @submit="handleCampaignSubmit" @cancel="campaignModalVisible = false" />
    </ModalDialog>

    <!-- Deployments -->
    <ModalDialog :visible="deployModalVisible" :title="t('ota.deployments')" :width="640" @close="deployModalVisible = false">
      <p v-if="deployLoading" class="loading-text">{{ t('common.loading') }}</p>
      <template v-else-if="campaignDetail">
        <DataTable :columns="deployColumns" :data="campaignDetail.deployments">
          <template #cell-status="{ row }">
            <StatusTag :status="row.status as string" type="ota" />
          </template>
        </DataTable>
      </template>
      <template #footer>
        <button class="btn btn-secondary" @click="deployModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.ota-management {
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
.action-buttons {
  display: flex;
  gap: 0.5rem;
}
.upload-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
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
  padding: 0.4rem 0.6rem;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  font-size: 0.875rem;
}
.form-textarea {
  min-height: 60px;
  resize: vertical;
}
.progress-bar {
  position: relative;
  height: 1.25rem;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--color-primary, #2563eb);
  transition: width 0.2s;
}
.progress-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  color: #fff;
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
.btn-secondary {
  background: #f1f5f9;
  color: #475569;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-link {
  background: none;
  border: none;
  color: var(--color-primary, #2563eb);
  cursor: pointer;
  padding: 0.2rem 0.4rem;
  font-size: 0.875rem;
}
.loading-text {
  color: #64748b;
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
