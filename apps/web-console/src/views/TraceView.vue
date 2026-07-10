<!--
  Function: Full-chain trace view with list, detail and replay
  Time: 2026-07-08
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePlatformStore } from '@/stores/platform'
import { listTraces, getTrace, getReplay } from '@/api/trace'
import type {
  Trace,
  TraceReplay,
  TraceListParams,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const platformStore = usePlatformStore()

interface TraceItem extends Trace {
  robot_id?: string
  mission_id?: string
}

const traces = ref<TraceItem[]>([])
const selectedTrace = ref<Trace | null>(null)
const replay = ref<TraceReplay | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const errorMsg = ref('')
const forbidden = ref(false)
const partialNote = ref('')

const filterRobotId = ref('')
const filterMissionId = ref('')
const filterStartTime = ref('')
const filterEndTime = ref('')

const hasPermission = computed(() =>
  authStore.permissions.includes('trace.trace.read')
)

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'PARTIAL' | 'STALE' | 'FORBIDDEN' | 'ERROR'

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && traces.value.length === 0) return 'LOADING'
  if (errorMsg.value && traces.value.length === 0) return 'ERROR'
  if (traces.value.length === 0 && !loading.value) return 'EMPTY'
  if (!platformStore.isOnline) return 'STALE'
  if (partialNote.value) return 'PARTIAL'
  return 'READY'
})

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadTraces(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }

  loading.value = true
  errorMsg.value = ''
  forbidden.value = false
  partialNote.value = ''

  try {
    const params: TraceListParams = {
      page_number: 1,
      page_size: 50,
      robot_id: filterRobotId.value || undefined,
      mission_id: filterMissionId.value || undefined,
      start_time: filterStartTime.value || undefined,
      end_time: filterEndTime.value || undefined
    }
    const result = await listTraces(params)
    traces.value = result.items as TraceItem[]
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

async function selectTrace(item: TraceItem): Promise<void> {
  detailLoading.value = true
  errorMsg.value = ''
  selectedTrace.value = item
  replay.value = null
  partialNote.value = ''

  try {
    const [detailResult, replayResult] = await Promise.all([
      getTrace(item.trace_id).catch((err: unknown) => {
        partialNote.value = resolveError(err as ProblemDetails)
        return null
      }),
      getReplay(item.trace_id).catch((err: unknown) => {
        if (!partialNote.value) {
          partialNote.value = resolveError(err as ProblemDetails)
        }
        return null
      })
    ])
    if (detailResult) {
      selectedTrace.value = detailResult
    }
    if (replayResult) {
      replay.value = replayResult
    }
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    detailLoading.value = false
  }
}

function handleSearch(): void {
  loadTraces()
}

onMounted(() => {
  if (hasPermission.value) {
    loadTraces()
  } else {
    forbidden.value = true
  }
})
</script>

<template>
  <div class="trace-view">
    <div class="page-header">
      <h2>{{ t('trace.title') }}</h2>
    </div>

    <!-- FORBIDDEN -->
    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">
      {{ t('trace.forbidden') }}
    </div>

    <template v-else>
      <!-- Filter bar -->
      <section class="card filter-bar">
        <div class="filter-group">
          <label>{{ t('trace.filter.robotId') }}</label>
          <input v-model="filterRobotId" type="text" class="filter-input" />
        </div>
        <div class="filter-group">
          <label>{{ t('trace.filter.missionId') }}</label>
          <input v-model="filterMissionId" type="text" class="filter-input" />
        </div>
        <div class="filter-group">
          <label>{{ t('trace.filter.startTime') }}</label>
          <input v-model="filterStartTime" type="datetime-local" class="filter-input" />
        </div>
        <div class="filter-group">
          <label>{{ t('trace.filter.endTime') }}</label>
          <input v-model="filterEndTime" type="datetime-local" class="filter-input" />
        </div>
        <button class="btn btn-primary" :disabled="loading" @click="handleSearch">
          {{ t('trace.filter.search') }}
        </button>
      </section>

      <!-- STALE indicator -->
      <div v-if="viewState === 'STALE'" class="alert alert-warning">
        {{ t('common.offline') }}
      </div>

      <!-- PARTIAL note -->
      <div v-if="viewState === 'PARTIAL' && partialNote" class="alert alert-warning">
        {{ partialNote }}
      </div>

      <!-- ERROR -->
      <div v-if="viewState === 'ERROR'" class="alert alert-error">
        {{ errorMsg }}
      </div>

      <!-- LOADING -->
      <div v-if="viewState === 'LOADING'" class="loading-text">
        {{ t('trace.loading') }}
      </div>

      <!-- EMPTY -->
      <div v-if="viewState === 'EMPTY'" class="empty-cell">
        {{ t('trace.empty') }}
      </div>

      <!-- READY / STALE / PARTIAL: show data -->
      <template v-if="['READY', 'STALE', 'PARTIAL'].includes(viewState)">
        <!-- Trace list -->
        <section class="card">
          <h3>{{ t('trace.list') }}</h3>
          <table class="trace-table">
            <thead>
              <tr>
                <th>{{ t('trace.columns.traceId') }}</th>
                <th>{{ t('trace.columns.robotId') }}</th>
                <th>{{ t('trace.columns.missionId') }}</th>
                <th>{{ t('trace.columns.startedAt') }}</th>
                <th>{{ t('trace.columns.duration') }}</th>
                <th>{{ t('trace.columns.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in traces"
                :key="item.id"
                class="trace-row"
                :class="{ 'trace-row-active': selectedTrace?.trace_id === item.trace_id }"
                @click="selectTrace(item)"
              >
                <td class="mono">
                  <button class="btn-link" @click.stop="router.push(`/traces/${item.trace_id}`)">
                    {{ item.trace_id }}
                  </button>
                </td>
                <td>{{ item.robot_id ?? '-' }}</td>
                <td>{{ item.mission_id ?? '-' }}</td>
                <td>{{ item.started_at }}</td>
                <td>{{ item.duration_ms != null ? item.duration_ms + ' ms' : '-' }}</td>
                <td>
                  <span class="status-badge" :class="`status-${item.status?.toLowerCase()}`">
                    {{ item.status }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- Detail + Replay -->
        <div v-if="detailLoading" class="loading-text">
          {{ t('trace.loading') }}
        </div>

        <template v-if="selectedTrace && !detailLoading">
          <!-- Detail panel -->
          <section class="card">
            <h3>{{ t('trace.detail') }}</h3>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">{{ t('trace.columns.traceId') }}</span>
                <span class="mono">{{ selectedTrace.trace_id }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">{{ t('trace.columns.status') }}</span>
                <span>{{ selectedTrace.status }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">{{ t('trace.columns.startedAt') }}</span>
                <span>{{ selectedTrace.started_at }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">{{ t('trace.columns.duration') }}</span>
                <span>{{ selectedTrace.duration_ms != null ? selectedTrace.duration_ms + ' ms' : '-' }}</span>
              </div>
              <div class="detail-item" v-if="selectedTrace.operation">
                <span class="detail-label">{{ t('common.action') }}</span>
                <span>{{ selectedTrace.operation }}</span>
              </div>
              <div class="detail-item" v-if="selectedTrace.resource_type">
                <span class="detail-label">{{ t('common.resource_type') }}</span>
                <span>{{ selectedTrace.resource_type }}</span>
              </div>
              <div class="detail-item" v-if="selectedTrace.resource_id">
                <span class="detail-label">{{ t('common.resource_id') }}</span>
                <span class="mono">{{ selectedTrace.resource_id }}</span>
              </div>
              <div class="detail-item" v-if="selectedTrace.actor_id">
                <span class="detail-label">{{ t('common.actor') }}</span>
                <span class="mono">{{ selectedTrace.actor_id }}</span>
              </div>
            </div>
          </section>

          <!-- Replay section -->
          <section v-if="replay" class="card">
            <h3>{{ t('trace.replay') }}</h3>

            <!-- Event sequence -->
            <div v-if="replay.events.length > 0" class="replay-section">
              <h4 class="replay-subtitle">{{ t('trace.eventSequence') }}</h4>
              <ol class="event-list">
                <li v-for="evt in replay.events" :key="evt.id" class="event-item">
                  <div class="event-header">
                    <span class="event-type">{{ evt.type }}</span>
                    <span class="event-time">{{ evt.occurred_at }}</span>
                  </div>
                  <details v-if="evt.payload && Object.keys(evt.payload).length > 0">
                    <summary class="event-payload-toggle">{{ t('trace.replayData') }}</summary>
                    <pre class="event-payload">{{ JSON.stringify(evt.payload, null, 2) }}</pre>
                  </details>
                </li>
              </ol>
            </div>

            <!-- Spans -->
            <div v-if="replay.spans.length > 0" class="replay-section">
              <h4 class="replay-subtitle">{{ t('trace.replayData') }}</h4>
              <table class="span-table">
                <thead>
                  <tr>
                    <th>{{ t('common.action') }}</th>
                    <th>service</th>
                    <th>{{ t('trace.columns.status') }}</th>
                    <th>{{ t('trace.columns.startedAt') }}</th>
                    <th>{{ t('trace.columns.duration') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="span in replay.spans" :key="span.id">
                    <td>{{ span.operation }}</td>
                    <td>{{ span.service }}</td>
                    <td>{{ span.status }}</td>
                    <td>{{ span.started_at }}</td>
                    <td>{{ span.duration_ms }} ms</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="replay.events.length === 0 && replay.spans.length === 0" class="empty-cell">
              {{ t('trace.empty') }}
            </div>
          </section>
        </template>
      </template>
    </template>
  </div>
</template>

<style scoped>
.trace-view {
  padding: 1rem;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.card h3 {
  margin: 0 0 0.75rem;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.75rem;
}
.filter-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.filter-group label {
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 500;
}
.filter-input {
  padding: 0.375rem 0.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  font-size: 0.875rem;
  min-width: 10rem;
}
.btn {
  cursor: pointer;
  border: none;
  border-radius: 4px;
  padding: 0.4rem 0.9rem;
  font-size: 0.875rem;
  height: fit-content;
}
.btn-primary {
  background: var(--color-primary, #2563eb);
  color: #fff;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.trace-table {
  width: 100%;
  border-collapse: collapse;
}
.trace-table th,
.trace-table td {
  text-align: left;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.875rem;
}
.trace-table th {
  font-weight: 600;
  color: #475569;
  background-color: #f8fafc;
}
.trace-row {
  cursor: pointer;
  transition: background-color 0.15s;
}
.trace-row:hover {
  background-color: #f1f5f9;
}
.trace-row-active {
  background-color: #dbeafe;
}
.mono {
  font-family: monospace;
  font-size: 0.8125rem;
}
.status-badge {
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}
.status-ok {
  background: #f0fdf4;
  color: #16a34a;
}
.status-error {
  background: #fef2f2;
  color: #dc2626;
}
.status-cancelled {
  background: #fefce8;
  color: #ca8a04;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 0.75rem;
}
.detail-item {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.detail-label {
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 500;
}
.replay-section {
  margin-top: 0.75rem;
}
.replay-subtitle {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 0.5rem;
}
.event-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.event-item {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 0.625rem 0.75rem;
  background-color: #f8fafc;
}
.event-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}
.event-type {
  font-weight: 600;
  font-size: 0.8125rem;
  color: #1e293b;
}
.event-time {
  font-size: 0.75rem;
  color: #64748b;
  font-family: monospace;
}
.event-payload-toggle {
  cursor: pointer;
  font-size: 0.75rem;
  color: #2563eb;
  margin-top: 0.375rem;
}
.event-payload {
  margin: 0.375rem 0 0;
  padding: 0.5rem;
  background-color: #1e293b;
  color: #e2e8f0;
  border-radius: 4px;
  font-size: 0.75rem;
  overflow-x: auto;
  max-height: 16rem;
}
.span-table {
  width: 100%;
  border-collapse: collapse;
}
.span-table th,
.span-table td {
  text-align: left;
  padding: 0.4rem 0.625rem;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.8125rem;
}
.span-table th {
  font-weight: 600;
  color: #475569;
  background-color: #f8fafc;
}
.empty-cell {
  text-align: center;
  color: #94a3b8;
  font-size: 0.875rem;
  padding: 1.5rem;
}
.loading-text {
  color: #64748b;
  font-size: 0.875rem;
  padding: 1rem 0;
}
.alert {
  padding: 0.625rem 0.875rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  margin-bottom: 1rem;
}
.alert-error {
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
}
.alert-warning {
  background-color: #fefce8;
  border: 1px solid #fef08a;
  color: #ca8a04;
}
.btn-link {
  background: transparent;
  border: none;
  color: #2563eb;
  cursor: pointer;
  font-size: inherit;
  font-family: inherit;
  padding: 0;
  text-decoration: underline;
}
</style>
