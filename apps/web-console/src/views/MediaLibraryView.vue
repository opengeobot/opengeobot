<script setup lang="ts">
// Function: Media library view with upload, grid view, download and delete
// Time: 2026-07-05
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ModalDialog from '@/components/ModalDialog.vue'
import { uploadMedia, listMedia, downloadMedia, deleteMedia } from '@/api/media'
import type {
  MediaAsset,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const media = ref<MediaAsset[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  keyword: '',
  mime_type: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 24,
  total: 0
})

const uploadProgress = ref(0)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const deleteTarget = ref<MediaAsset | null>(null)
const deleteVisible = ref(false)

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadMedia(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listMedia({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      keyword: filters.keyword || undefined,
      mime_type: filters.mime_type || undefined
    })
    media.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadMedia()
}

function handleReset(): void {
  filters.keyword = ''
  filters.mime_type = ''
  pagination.value.page_number = 1
  loadMedia()
}

function triggerUpload(): void {
  fileInput.value?.click()
}

async function handleFileChange(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  uploading.value = true
  uploadProgress.value = 0
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await uploadMedia(file, (percent: number) => {
      uploadProgress.value = percent
    })
    successMsg.value = t('media.upload_success')
    await loadMedia()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    uploading.value = false
    uploadProgress.value = 0
    target.value = ''
  }
}

async function handleDownload(asset: MediaAsset): Promise<void> {
  errorMsg.value = ''
  try {
    const blob = await downloadMedia(asset.id)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = asset.file_name
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

function openDelete(asset: MediaAsset): void {
  deleteTarget.value = asset
  deleteVisible.value = true
}

async function handleDeleteConfirm(): Promise<void> {
  if (!deleteTarget.value) return
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await deleteMedia(deleteTarget.value.id)
    deleteVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadMedia()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

const totalPages = computed<number>(() => {
  return Math.max(1, Math.ceil(pagination.value.total / pagination.value.page_size))
})

function handlePrevPage(): void {
  if (pagination.value.page_number > 1) {
    pagination.value.page_number -= 1
    loadMedia()
  }
}

function handleNextPage(): void {
  if (pagination.value.page_number < totalPages.value) {
    pagination.value.page_number += 1
    loadMedia()
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

function thumbStyle(asset: MediaAsset): Record<string, string> {
  if (asset.thumbnail_url) {
    return { backgroundImage: `url(${asset.thumbnail_url})` }
  }
  return {}
}

onMounted(() => {
  loadMedia()
})
</script>

<template>
  <div class="media-library">
    <h1 class="page-title">{{ t('media.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('media.file_name')"
        @keyup.enter="handleSearch"
      />
      <input v-model="filters.mime_type" class="filter-input-sm" type="text" :placeholder="t('media.mime_type')" />
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button
        v-permission="'platform.media.manage'"
        class="btn btn-primary"
        :disabled="uploading"
        @click="triggerUpload"
      >
        {{ uploading ? `${t('media.uploading')} ${uploadProgress}%` : t('media.upload') }}
      </button>
      <input
        ref="fileInput"
        type="file"
        class="hidden-input"
        @change="handleFileChange"
      />
    </div>

    <div v-if="uploading" class="progress-bar">
      <div class="progress-fill" :style="{ width: `${uploadProgress}%` }" />
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <p v-if="loading" class="loading-text">{{ t('common.loading') }}</p>
    <div v-else-if="media.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
    <div v-else class="media-grid">
      <div v-for="asset in media" :key="asset.id" class="media-card">
        <div class="media-thumb" :style="thumbStyle(asset)">
          <span v-if="!asset.thumbnail_url" class="thumb-placeholder">{{ asset.file_name.split('.').pop()?.toUpperCase() }}</span>
        </div>
        <div class="media-info">
          <span class="media-name" :title="asset.file_name">{{ asset.file_name }}</span>
          <span class="media-meta">{{ asset.mime_type }} · {{ formatSize(asset.size) }}</span>
        </div>
        <div class="media-actions">
          <button class="btn-link" @click="handleDownload(asset)">{{ t('media.download') }}</button>
          <button v-permission="'platform.media.manage'" class="btn-link btn-danger" @click="openDelete(asset)">
            {{ t('common.delete') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="pagination.total > 0" class="pagination-bar">
      <span class="pagination-total">{{ t('common.total') }}: {{ pagination.total }}</span>
      <div class="pagination-controls">
        <button class="page-btn" :disabled="pagination.page_number <= 1" @click="handlePrevPage">‹</button>
        <span class="page-indicator">{{ pagination.page_number }} / {{ totalPages }}</span>
        <button class="page-btn" :disabled="pagination.page_number >= totalPages" @click="handleNextPage">›</button>
      </div>
    </div>

    <!-- Delete confirm -->
    <ModalDialog
      :visible="deleteVisible"
      :title="t('common.delete')"
      :width="400"
      @close="deleteVisible = false"
    >
      <p class="confirm-text">{{ t('common.confirm_delete') }}</p>
      <template #footer>
        <button class="btn btn-danger" @click="handleDeleteConfirm">{{ t('common.delete') }}</button>
        <button class="btn btn-secondary" @click="deleteVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.media-library {
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
  min-width: 12rem;
}

.filter-input-sm {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  min-width: 9rem;
}

.hidden-input {
  display: none;
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

.btn-danger {
  background-color: #dc2626;
  color: #ffffff;
}

.btn-danger:hover {
  background-color: #b91c1c;
}

.progress-bar {
  height: 0.375rem;
  background-color: #f1f5f9;
  border-radius: 0.1875rem;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: #2563eb;
  transition: width 0.2s;
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

.loading-text {
  color: #64748b;
  font-size: 0.875rem;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  font-size: 0.875rem;
  padding: 2rem;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: 0.75rem;
}

.media-card {
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.media-thumb {
  height: 8rem;
  background-color: #f1f5f9;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-placeholder {
  font-size: 1.25rem;
  font-weight: 700;
  color: #94a3b8;
}

.media-info {
  padding: 0.5rem 0.625rem;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.media-name {
  font-size: 0.8125rem;
  font-weight: 500;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.media-meta {
  font-size: 0.7rem;
  color: #64748b;
}

.media-actions {
  display: flex;
  gap: 0.75rem;
  padding: 0.375rem 0.625rem 0.625rem;
  border-top: 1px solid #f1f5f9;
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

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 0;
}

.pagination-total {
  font-size: 0.8125rem;
  color: #64748b;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.page-btn {
  padding: 0.25rem 0.625rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.25rem;
  background-color: #ffffff;
  font-size: 0.875rem;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  background-color: #f1f5f9;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-indicator {
  font-size: 0.8125rem;
  color: #475569;
  min-width: 3rem;
  text-align: center;
}

.confirm-text {
  font-size: 0.875rem;
  color: #334155;
}
</style>
