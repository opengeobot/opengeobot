<script setup lang="ts">
// Function: Map management view with areas and restricted areas
// Time: 2026-07-05
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listMaps,
  createMap,
  updateMap,
  publishMap,
  listAreas,
  createArea,
  listRestrictedAreas,
  createRestrictedArea
} from '@/api/map'
import type {
  GameMap,
  MapArea,
  RestrictedArea,
  FormField,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const maps = ref<GameMap[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const filters = reactive({
  keyword: '',
  status: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'map_name', title: t('map.name'), sortable: true },
  { key: 'map_code', title: t('map.code') },
  { key: 'status', title: t('common.status') },
  { key: 'version', title: t('common.version') },
  { key: 'frame', title: t('map.frame') }
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('status.publish.draft'), value: 'draft' },
  { label: t('status.publish.published'), value: 'published' },
  { label: t('status.publish.archived'), value: 'archived' }
])

const areaTypeOptions = computed<SelectOption[]>(() => [
  { label: t('map.area_type_room'), value: 'room' },
  { label: t('map.area_type_corridor'), value: 'corridor' },
  { label: t('map.area_type_charging'), value: 'charging' },
  { label: t('map.area_type_workstation'), value: 'workstation' }
])

const restrictedLevelOptions = computed<SelectOption[]>(() => [
  { label: t('safety.level_warning'), value: 'warning' },
  { label: t('safety.level_critical'), value: 'critical' }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadMaps(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listMaps({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined
    })
    maps.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadMaps()
}

function handleReset(): void {
  filters.keyword = ''
  filters.status = ''
  pagination.value.page_number = 1
  loadMaps()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadMaps()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadMaps()
}

// ---- Create / Edit map ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<Record<string, unknown>>({})

const formFields = computed<FormField[]>(() => [
  { key: 'map_name', label: t('map.name'), type: 'text', required: true },
  { key: 'map_code', label: t('map.code'), type: 'text', required: true },
  { key: 'frame', label: t('map.frame'), type: 'text' },
  { key: 'description', label: t('common.description'), type: 'textarea' }
])

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formVisible.value = true
}

function openEdit(row: GameMap): void {
  formMode.value = 'edit'
  formModel.id = row.id
  formModel.map_name = row.map_name
  formModel.map_code = row.map_code
  formModel.frame = row.frame
  formModel.description = row.description
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'create') {
      await createMap({
        map_code: String(data.map_code),
        map_name: String(data.map_name),
        frame: String(data.frame ?? ''),
        description: String(data.description ?? '')
      })
    } else {
      await updateMap(String(formModel.id), {
        map_name: String(data.map_name),
        frame: String(data.frame ?? ''),
        description: String(data.description ?? '')
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadMaps()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handlePublish(row: GameMap): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await publishMap(row.id)
    successMsg.value = t('common.operation_success')
    await loadMaps()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Areas panel ----

const areaVisible = ref(false)
const areaTarget = ref<GameMap | null>(null)
const areas = ref<MapArea[]>([])
const restrictedAreas = ref<RestrictedArea[]>([])
const areaLoading = ref(false)
const areaTab = ref<'area' | 'restricted'>('area')

// area create form
const areaForm = reactive<{ area_code: string; area_name: string; area_type: string; polygon: string }>({
  area_code: '',
  area_name: '',
  area_type: 'room',
  polygon: '{}'
})
const restrictedForm = reactive<{ area_code: string; area_name: string; level: string; polygon: string }>({
  area_code: '',
  area_name: '',
  level: 'warning',
  polygon: '{}'
})

async function openAreas(row: GameMap): Promise<void> {
  areaTarget.value = row
  areaVisible.value = true
  areaTab.value = 'area'
  areaLoading.value = true
  errorMsg.value = ''
  try {
    const [areaList, restrictedList] = await Promise.all([
      listAreas(row.id),
      listRestrictedAreas(row.id)
    ])
    areas.value = areaList
    restrictedAreas.value = restrictedList
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    areaLoading.value = false
  }
}

function parsePolygon(text: string): Record<string, unknown> {
  try {
    return JSON.parse(text) as Record<string, unknown>
  } catch {
    return {}
  }
}

async function handleCreateArea(): Promise<void> {
  if (!areaTarget.value) return
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await createArea(areaTarget.value.id, {
      area_code: areaForm.area_code,
      area_name: areaForm.area_name,
      area_type: areaForm.area_type,
      polygon: parsePolygon(areaForm.polygon)
    })
    successMsg.value = t('common.operation_success')
    areaForm.area_code = ''
    areaForm.area_name = ''
    areaForm.polygon = '{}'
    areas.value = await listAreas(areaTarget.value.id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleCreateRestrictedArea(): Promise<void> {
  if (!areaTarget.value) return
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await createRestrictedArea(areaTarget.value.id, {
      area_code: restrictedForm.area_code,
      area_name: restrictedForm.area_name,
      level: restrictedForm.level,
      polygon: parsePolygon(restrictedForm.polygon)
    })
    successMsg.value = t('common.operation_success')
    restrictedForm.area_code = ''
    restrictedForm.area_name = ''
    restrictedForm.polygon = '{}'
    restrictedAreas.value = await listRestrictedAreas(areaTarget.value.id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadMaps()
})
</script>

<template>
  <div class="map-management">
    <h1 class="page-title">{{ t('map.title') }}</h1>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('map.name')"
        @keyup.enter="handleSearch"
      />
      <select v-model="filters.status" class="filter-select">
        <option v-for="opt in statusFilterOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleReset">{{ t('common.reset') }}</button>
      <button v-permission="'platform.map.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="maps"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #cell-status="{ row }">
        <StatusTag :status="row.status as string" type="publish" />
      </template>
      <template #actions="{ row }">
        <div class="action-buttons">
          <button v-permission="'platform.map.manage'" class="btn-link" @click="openEdit(row as unknown as GameMap)">
            {{ t('common.edit') }}
          </button>
          <button v-permission="'platform.map.manage'" class="btn-link" @click="handlePublish(row as unknown as GameMap)">
            {{ t('common.publish') }}
          </button>
          <button class="btn-link" @click="openAreas(row as unknown as GameMap)">
            {{ t('map.areas') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Create / Edit map -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('map.create_title') : t('map.edit_title')"
      :width="520"
      @close="formVisible = false"
    >
      <FormBuilder
        :fields="formFields"
        :model-value="formModel"
        @submit="handleFormSubmit"
        @cancel="formVisible = false"
      />
    </ModalDialog>

    <!-- Areas panel -->
    <ModalDialog
      :visible="areaVisible"
      :title="t('map.areas_title')"
      :width="720"
      @close="areaVisible = false"
    >
      <div v-if="areaTarget" class="area-header">
        <span class="area-map-name">{{ areaTarget.map_name }}</span>
        <span class="area-map-code">{{ areaTarget.map_code }}</span>
      </div>

      <div class="area-tabs">
        <button
          class="tab-btn"
          :class="{ 'tab-active': areaTab === 'area' }"
          @click="areaTab = 'area'"
        >
          {{ t('map.areas') }} ({{ areas.length }})
        </button>
        <button
          class="tab-btn"
          :class="{ 'tab-active': areaTab === 'restricted' }"
          @click="areaTab = 'restricted'"
        >
          {{ t('map.restricted_areas') }} ({{ restrictedAreas.length }})
        </button>
      </div>

      <p v-if="areaLoading" class="loading-text">{{ t('common.loading') }}</p>

      <!-- Areas tab -->
      <div v-else-if="areaTab === 'area'">
        <ul class="area-list">
          <li v-for="a in areas" :key="a.id" class="area-item">
            <span class="area-item-name">{{ a.area_name }}</span>
            <span class="area-item-code">{{ a.area_code }}</span>
            <StatusTag :status="a.area_type" type="health" />
          </li>
        </ul>
        <div v-if="areas.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
        <div v-permission="'platform.map.manage'" class="area-create">
          <h4 class="steps-title">{{ t('map.create_area') }}</h4>
          <div class="form-field">
            <label class="form-label">{{ t('map.area_name') }}</label>
            <input v-model="areaForm.area_name" class="form-input" type="text" />
          </div>
          <div class="form-row">
            <div class="form-field">
              <label class="form-label">{{ t('map.area_code') }}</label>
              <input v-model="areaForm.area_code" class="form-input" type="text" />
            </div>
            <div class="form-field">
              <label class="form-label">{{ t('map.area_type') }}</label>
              <select v-model="areaForm.area_type" class="form-input">
                <option v-for="opt in areaTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
          </div>
          <div class="form-field">
            <label class="form-label">{{ t('map.polygon') }}</label>
            <textarea v-model="areaForm.polygon" class="form-input form-textarea" />
          </div>
          <button class="btn btn-primary btn-sm" @click="handleCreateArea">{{ t('common.create') }}</button>
        </div>
      </div>

      <!-- Restricted areas tab -->
      <div v-else>
        <ul class="area-list">
          <li v-for="a in restrictedAreas" :key="a.id" class="area-item">
            <span class="area-item-name">{{ a.area_name }}</span>
            <span class="area-item-code">{{ a.area_code }}</span>
            <StatusTag :status="a.level" type="health" />
          </li>
        </ul>
        <div v-if="restrictedAreas.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
        <div v-permission="'platform.map.manage'" class="area-create">
          <h4 class="steps-title">{{ t('map.create_restricted') }}</h4>
          <div class="form-field">
            <label class="form-label">{{ t('map.area_name') }}</label>
            <input v-model="restrictedForm.area_name" class="form-input" type="text" />
          </div>
          <div class="form-row">
            <div class="form-field">
              <label class="form-label">{{ t('map.area_code') }}</label>
              <input v-model="restrictedForm.area_code" class="form-input" type="text" />
            </div>
            <div class="form-field">
              <label class="form-label">{{ t('map.restricted_level') }}</label>
              <select v-model="restrictedForm.level" class="form-input">
                <option v-for="opt in restrictedLevelOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
          </div>
          <div class="form-field">
            <label class="form-label">{{ t('map.polygon') }}</label>
            <textarea v-model="restrictedForm.polygon" class="form-input form-textarea" />
          </div>
          <button class="btn btn-primary btn-sm" @click="handleCreateRestrictedArea">{{ t('common.create') }}</button>
        </div>
      </div>

      <template #footer>
        <button class="btn btn-secondary" @click="areaVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.map-management {
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

.btn-sm {
  padding: 0.25rem 0.75rem;
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

.area-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.area-map-name {
  font-weight: 700;
  font-size: 1rem;
  color: #1e293b;
}

.area-map-code {
  font-family: monospace;
  font-size: 0.75rem;
  color: #2563eb;
}

.area-tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  border-bottom: 1px solid #e2e8f0;
}

.tab-btn {
  padding: 0.5rem 0.875rem;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 0.8125rem;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.tab-btn.tab-active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  font-weight: 600;
}

.area-list {
  list-style: none;
  margin: 0 0 0.75rem;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.area-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.625rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
}

.area-item-name {
  font-size: 0.8125rem;
  font-weight: 500;
  color: #1e293b;
}

.area-item-code {
  font-family: monospace;
  font-size: 0.7rem;
  color: #64748b;
  margin-left: auto;
}

.area-create {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.steps-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #334155;
  margin: 0;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.form-label {
  font-size: 0.8125rem;
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

.form-textarea {
  min-height: 3rem;
  resize: vertical;
  font-family: monospace;
  font-size: 0.75rem;
}

.form-row {
  display: flex;
  gap: 0.75rem;
}

.form-row .form-field {
  flex: 1;
}

.loading-text {
  color: #64748b;
  font-size: 0.875rem;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  font-size: 0.875rem;
  padding: 1.5rem;
}
</style>
