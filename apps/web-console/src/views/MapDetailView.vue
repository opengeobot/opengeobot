<!--
  Function: Map detail with areas and restricted areas
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getMap, listAreas, listRestrictedAreas } from '@/api/map'
import type {
  GameMap,
  MapArea,
  RestrictedArea,
  DataTableColumn,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const mapId = computed(() => String(route.params.mapId ?? ''))
const mapInfo = ref<GameMap | null>(null)
const areas = ref<MapArea[]>([])
const restricted = ref<RestrictedArea[]>([])
const loading = ref(false)
const errorMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() => authStore.permissions.includes('map.map.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !mapInfo.value) return 'LOADING'
  if (errorMsg.value && !mapInfo.value) return 'ERROR'
  return 'READY'
})

const areaColumns = computed<DataTableColumn[]>(() => [
  { key: 'area_name', title: t('map.area_name') },
  { key: 'area_code', title: t('map.area_code') },
  { key: 'area_type', title: t('map.area_type') }
])

const restrictedColumns = computed<DataTableColumn[]>(() => [
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

async function loadDetail(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    mapInfo.value = await getMap(mapId.value)
    const [areaResult, restrictedResult] = await Promise.all([
      listAreas(mapId.value).catch(() => [] as MapArea[]),
      listRestrictedAreas(mapId.value).catch(() => [] as RestrictedArea[])
    ])
    areas.value = areaResult
    restricted.value = restrictedResult
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (hasPermission.value) loadDetail()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('map.detail_title') }}</h1>
      <div class="header-actions">
        <button class="btn btn-secondary" @click="router.push('/restricted-areas')">
          {{ t('nav.restricted_areas') }}
        </button>
        <button class="btn btn-secondary" @click="router.push('/maps')">{{ t('common.cancel') }}</button>
      </div>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('map.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="mapInfo">
      <section class="card">
        <div class="info-grid">
          <div><span class="label">{{ t('map.name') }}</span><span>{{ mapInfo.map_name }}</span></div>
          <div><span class="label">{{ t('map.code') }}</span><span class="mono">{{ mapInfo.map_code }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="mapInfo.status" type="publish" /></div>
          <div><span class="label">{{ t('map.frame') }}</span><span>{{ mapInfo.frame }}</span></div>
          <div><span class="label">{{ t('common.version') }}</span><span>{{ mapInfo.version }}</span></div>
          <div><span class="label">{{ t('common.description') }}</span><span>{{ mapInfo.description || '-' }}</span></div>
        </div>
      </section>

      <section class="card">
        <h2 class="section-title">{{ t('map.areas') }}</h2>
        <DataTable :columns="areaColumns" :data="areas" />
      </section>

      <section class="card">
        <h2 class="section-title">{{ t('map.restricted_areas') }}</h2>
        <DataTable :columns="restrictedColumns" :data="restricted" />
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.header-actions { display: flex; gap: 0.5rem; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); gap: 1rem; }
.label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.25rem; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; }
.section-title { font-size: 1rem; font-weight: 600; margin: 0 0 0.75rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.loading-text { color: #64748b; }
</style>
