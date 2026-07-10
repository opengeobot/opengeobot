<!--
  Function: Restricted area management — pick map then list/create
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import { listMaps, listRestrictedAreas, createRestrictedArea } from '@/api/map'
import type {
  GameMap,
  RestrictedArea,
  FormField,
  SelectOption,
  DataTableColumn,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const maps = ref<GameMap[]>([])
const selectedMapId = ref('')
const rows = ref<RestrictedArea[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() =>
  authStore.permissions.includes('map.restricted_area.manage') ||
  authStore.permissions.includes('map.map.read')
)

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (!selectedMapId.value) return 'EMPTY'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const mapOptions = computed<SelectOption[]>(() =>
  maps.value.map((m) => ({ label: m.map_name, value: m.id }))
)

const columns = computed<DataTableColumn[]>(() => [
  { key: 'area_name', title: t('map.area_name') },
  { key: 'area_code', title: t('map.area_code') },
  { key: 'level', title: t('map.restricted_level') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadMaps(): Promise<void> {
  try {
    const result = await listMaps({ page_number: 1, page_size: 100 })
    maps.value = result.items
    if (!selectedMapId.value && maps.value.length > 0) {
      selectedMapId.value = maps.value[0].id
    }
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  }
}

async function loadRestricted(): Promise<void> {
  if (!selectedMapId.value) {
    rows.value = []
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    rows.value = await listRestrictedAreas(selectedMapId.value)
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

watch(selectedMapId, () => {
  loadRestricted()
})

const formVisible = ref(false)
const formModel = reactive<Record<string, unknown>>({
  polygon: '{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}'
})

const formFields = computed<FormField[]>(() => [
  { key: 'area_code', label: t('map.area_code'), type: 'text', required: true },
  { key: 'area_name', label: t('map.area_name'), type: 'text', required: true },
  {
    key: 'level',
    label: t('map.restricted_level'),
    type: 'select',
    required: true,
    options: [
      { label: 'HIGH', value: 'HIGH' },
      { label: 'MEDIUM', value: 'MEDIUM' },
      { label: 'LOW', value: 'LOW' }
    ]
  },
  { key: 'polygon', label: t('map.polygon'), type: 'textarea', required: true }
])

async function handleCreate(values: Record<string, unknown>): Promise<void> {
  if (!selectedMapId.value) return
  try {
    const polygon = JSON.parse(String(values.polygon))
    await createRestrictedArea(selectedMapId.value, {
      area_code: String(values.area_code),
      area_name: String(values.area_name),
      level: String(values.level),
      polygon
    })
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadRestricted()
  } catch (err) {
    if (err instanceof SyntaxError) {
      errorMsg.value = t('map.invalid_polygon')
    } else {
      errorMsg.value = resolveError(err as ProblemDetails)
    }
  }
}

onMounted(async () => {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  await loadMaps()
  await loadRestricted()
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('map.restricted_title') }}</h1>
    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('map.forbidden') }}</div>
    <template v-else>
      <div class="toolbar">
        <select v-model="selectedMapId" class="filter-select">
          <option value="">{{ t('map.select_map') }}</option>
          <option v-for="opt in mapOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <button class="btn btn-secondary" :disabled="!selectedMapId" @click="loadRestricted">
          {{ t('common.refresh') }}
        </button>
        <button
          v-permission="'map.restricted_area.manage'"
          class="btn btn-primary"
          :disabled="!selectedMapId"
          @click="formVisible = true"
        >
          {{ t('map.create_restricted') }}
        </button>
      </div>
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>
      <p v-if="!selectedMapId" class="empty-text">{{ t('map.select_map_hint') }}</p>
      <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
      <p v-else-if="viewState === 'EMPTY'" class="empty-text">{{ t('common.no_data') }}</p>
      <DataTable
        v-if="selectedMapId && (viewState === 'READY' || rows.length > 0)"
        :columns="columns"
        :data="rows"
        :loading="loading"
      />
    </template>

    <ModalDialog :visible="formVisible" :title="t('map.create_restricted')" :width="520" @close="formVisible = false">
      <FormBuilder :fields="formFields" :model-value="formModel" @submit="handleCreate" @cancel="formVisible = false" />
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.toolbar { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.filter-select { padding: 0.5rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 0.375rem; min-width: 14rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text, .empty-text { color: #64748b; }
</style>
