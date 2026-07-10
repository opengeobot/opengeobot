<!--
  Function: Trace detail and replay page
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
import { getTrace, getReplay } from '@/api/trace'
import type { Trace, TraceReplay, DataTableColumn, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const traceId = computed(() => String(route.params.traceId ?? ''))
const trace = ref<Trace | null>(null)
const replay = ref<TraceReplay | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const partialNote = ref('')
const forbidden = ref(false)

const hasPermission = computed(() => authStore.permissions.includes('trace.trace.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !trace.value) return 'LOADING'
  if (errorMsg.value && !trace.value) return 'ERROR'
  return 'READY'
})

const spanColumns = computed<DataTableColumn[]>(() => [
  { key: 'operation', title: t('trace.span_operation') },
  { key: 'service', title: t('trace.span_service') },
  { key: 'status', title: t('common.status') },
  { key: 'started_at', title: t('common.start_time') },
  { key: 'duration_ms', title: t('trace.columns.duration') }
])

const eventColumns = computed<DataTableColumn[]>(() => [
  { key: 'occurred_at', title: t('common.occurred_at') },
  { key: 'type', title: t('common.type') },
  { key: 'id', title: 'ID' }
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
  partialNote.value = ''
  try {
    const [detail, replayResult] = await Promise.all([
      getTrace(traceId.value),
      getReplay(traceId.value).catch((err: unknown) => {
        partialNote.value = resolveError(err as ProblemDetails)
        return null
      })
    ])
    trace.value = detail
    replay.value = replayResult
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
      <h1 class="page-title">{{ t('trace.detail') }}</h1>
      <button class="btn btn-secondary" @click="router.push('/trace')">{{ t('common.cancel') }}</button>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('trace.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('trace.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="trace">
      <p v-if="partialNote" class="alert alert-warning">{{ partialNote }}</p>
      <section class="card">
        <div class="info-grid">
          <div><span class="label">{{ t('trace.columns.traceId') }}</span><span class="mono">{{ trace.trace_id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="trace.status" type="task" /></div>
          <div><span class="label">{{ t('common.action') }}</span><span>{{ trace.operation }}</span></div>
          <div><span class="label">{{ t('common.resource_type') }}</span><span>{{ trace.resource_type }}</span></div>
          <div><span class="label">{{ t('common.resource_id') }}</span><span class="mono">{{ trace.resource_id }}</span></div>
          <div><span class="label">{{ t('common.actor') }}</span><span>{{ trace.actor_id }}</span></div>
          <div><span class="label">{{ t('common.start_time') }}</span><span>{{ trace.started_at }}</span></div>
          <div><span class="label">{{ t('trace.columns.duration') }}</span><span>{{ trace.duration_ms != null ? trace.duration_ms + ' ms' : '-' }}</span></div>
        </div>
      </section>

      <section v-if="replay" class="card">
        <h2 class="section-title">{{ t('trace.replay') }}</h2>
        <h3 class="sub-title">{{ t('trace.replayData') }}</h3>
        <DataTable :columns="spanColumns" :data="replay.spans ?? []">
          <template #cell-status="{ row }">
            <StatusTag :status="row.status as string" type="task" />
          </template>
        </DataTable>
        <h3 class="sub-title">{{ t('trace.eventSequence') }}</h3>
        <DataTable :columns="eventColumns" :data="replay.events ?? []" />
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); gap: 1rem; }
.label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.25rem; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; word-break: break-all; }
.section-title { font-size: 1rem; font-weight: 600; margin: 0 0 0.75rem; }
.sub-title { font-size: 0.875rem; font-weight: 600; margin: 1rem 0 0.5rem; color: #475569; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-warning { background-color: #fffbeb; border: 1px solid #fde68a; color: #b45309; }
.loading-text { color: #64748b; }
</style>
