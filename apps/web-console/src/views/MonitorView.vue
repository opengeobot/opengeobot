<script setup lang="ts">
// Function: Real-time monitoring view with overview, robot grid and WebSocket feed
// Time: 2026-07-05
// Author: AxeXie
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import StatusTag from '@/components/StatusTag.vue'
import { getOverview, getRobotMonitor, getMissionMonitor, takeover } from '@/api/monitor'
import { listRobots } from '@/api/robot'
import { listMissions } from '@/api/mission'
import { useAuthStore } from '@/stores/auth'
import type {
  MonitorOverview,
  RobotMonitor,
  MissionMonitor,
  Robot,
  Mission,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const authStore = useAuthStore()

const overview = ref<MonitorOverview>({
  total_robots: 0,
  online_robots: 0,
  busy_robots: 0,
  active_missions: 0,
  alerts: 0
})
const robotMonitors = ref<RobotMonitor[]>([])
const missionMonitors = ref<MissionMonitor[]>([])
const loading = ref(false)
const errorMsg = ref('')
const wsConnected = ref(false)
const wsError = ref('')

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const overviewCards = computed(() => [
  { key: 'monitor.total_robots', value: overview.value.total_robots, accent: 'blue' },
  { key: 'monitor.online_robots', value: overview.value.online_robots, accent: 'green' },
  { key: 'monitor.busy_robots', value: overview.value.busy_robots, accent: 'amber' },
  { key: 'monitor.active_missions', value: overview.value.active_missions, accent: 'blue' },
  { key: 'monitor.alerts', value: overview.value.alerts, accent: 'red' }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadOverview(): Promise<void> {
  errorMsg.value = ''
  try {
    overview.value = await getOverview()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function loadRobots(): Promise<void> {
  try {
    const result = await listRobots({ page_number: 1, page_size: 200 })
    const monitors: RobotMonitor[] = []
    for (const r of result.items) {
      try {
        monitors.push(await getRobotMonitor(r.id))
      } catch {
        monitors.push({
          robot_id: r.id,
          robot_name: r.name,
          status: r.status,
          battery: 0,
          position: { x: 0, y: 0, yaw: 0 },
          current_mission_id: null,
          last_seen: r.last_seen ?? ''
        })
      }
    }
    robotMonitors.value = monitors
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function loadMissions(): Promise<void> {
  try {
    const result = await listMissions({ page_number: 1, page_size: 50, status: 'running' })
    const monitors: MissionMonitor[] = []
    for (const m of result.items) {
      try {
        monitors.push(await getMissionMonitor(m.id))
      } catch {
        monitors.push({
          mission_id: m.id,
          mission_name: m.name,
          robot_name: m.robot_name ?? m.robot_id,
          status: m.status,
          progress: 0,
          current_step: 0,
          total_steps: m.steps?.length ?? 0,
          trace_id: m.id
        })
      }
    }
    missionMonitors.value = monitors
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function loadAll(): Promise<void> {
  loading.value = true
  await Promise.all([loadOverview(), loadRobots(), loadMissions()])
  loading.value = false
}

// ---- WebSocket real-time updates ----

function buildWsUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const token = localStorage.getItem('token') ?? ''
  return `${proto}://${window.location.host}/ws/monitor?token=${encodeURIComponent(token)}`
}

function handleWsMessage(event: MessageEvent): void {
  try {
    const msg = JSON.parse(event.data as string) as { type: string; payload: Record<string, unknown> }
    if (msg.type === 'overview') {
      overview.value = msg.payload as unknown as MonitorOverview
    } else if (msg.type === 'robot') {
      const data = msg.payload as unknown as RobotMonitor
      const idx = robotMonitors.value.findIndex((r) => r.robot_id === data.robot_id)
      if (idx >= 0) robotMonitors.value[idx] = data
      else robotMonitors.value.push(data)
    } else if (msg.type === 'mission') {
      const data = msg.payload as unknown as MissionMonitor
      const idx = missionMonitors.value.findIndex((m) => m.mission_id === data.mission_id)
      if (idx >= 0) missionMonitors.value[idx] = data
      else missionMonitors.value.push(data)
    }
  } catch {
    // Ignore malformed messages
  }
}

function connectWs(): void {
  try {
    ws = new WebSocket(buildWsUrl())
  } catch (err) {
    wsError.value = (err as Error).message
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    wsConnected.value = true
    wsError.value = ''
  }

  ws.onmessage = handleWsMessage

  ws.onerror = () => {
    wsError.value = t('monitor.ws_error')
  }

  ws.onclose = () => {
    wsConnected.value = false
    scheduleReconnect()
  }
}

function scheduleReconnect(): void {
  if (reconnectTimer) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectWs()
  }, 5000)
}

function disconnectWs(): void {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.onerror = null
    ws.onmessage = null
    if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
      ws.close()
    }
    ws = null
  }
  wsConnected.value = false
}

// ---- Takeover ----

async function handleTakeover(robotId: string): Promise<void> {
  errorMsg.value = ''
  try {
    await takeover(robotId, {
      operator_id: authStore.user?.id ?? '',
      reason: 'manual takeover'
    })
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadAll()
  connectWs()
})

onUnmounted(() => {
  disconnectWs()
})
</script>

<template>
  <div class="monitor-view">
    <div class="monitor-header">
      <h1 class="page-title">{{ t('monitor.title') }}</h1>
      <span class="ws-status" :class="wsConnected ? 'ws-on' : 'ws-off'">
        {{ wsConnected ? t('monitor.ws_connected') : t('monitor.ws_disconnected') }}
      </span>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="loading" class="loading-text">{{ t('common.loading') }}</p>

    <div class="card-grid">
      <div
        v-for="card in overviewCards"
        :key="card.key"
        class="status-card"
        :class="`accent-${card.accent}`"
      >
        <span class="card-label">{{ t(card.key) }}</span>
        <span class="card-value">{{ card.value }}</span>
      </div>
    </div>

    <section class="section">
      <h2 class="section-title">{{ t('monitor.robot_grid') }}</h2>
      <div v-if="robotMonitors.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
      <div class="robot-grid">
        <div v-for="r in robotMonitors" :key="r.robot_id" class="robot-card">
          <div class="robot-card-header">
            <span class="robot-name">{{ r.robot_name }}</span>
            <StatusTag :status="r.status" type="robot" />
          </div>
          <div class="robot-meta">
            <span>{{ t('monitor.battery') }}: {{ r.battery }}%</span>
            <span>{{ t('monitor.position') }}: ({{ r.position.x.toFixed(2) }}, {{ r.position.y.toFixed(2) }})</span>
          </div>
          <div class="battery-bar">
            <div class="battery-fill" :style="{ width: `${Math.max(0, Math.min(100, r.battery))}%` }" />
          </div>
          <button
            v-permission="'platform.monitor.manage'"
            class="btn btn-secondary btn-sm"
            @click="handleTakeover(r.robot_id)"
          >
            {{ t('monitor.takeover') }}
          </button>
        </div>
      </div>
    </section>

    <section class="section">
      <h2 class="section-title">{{ t('monitor.active_missions') }}</h2>
      <div v-if="missionMonitors.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
      <ul v-else class="mission-monitor-list">
        <li v-for="m in missionMonitors" :key="m.mission_id" class="mission-monitor-item">
          <div class="mission-monitor-header">
            <span class="mission-monitor-name">{{ m.mission_name }}</span>
            <StatusTag :status="m.status" type="task" />
          </div>
          <div class="mission-monitor-meta">
            <span>{{ t('mission.robot') }}: {{ m.robot_name }}</span>
            <span>{{ t('monitor.progress') }}: {{ m.current_step }}/{{ m.total_steps }}</span>
            <span>{{ t('common.trace_id') }}: {{ m.trace_id }}</span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: `${Math.max(0, Math.min(100, m.progress))}%` }" />
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.monitor-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.monitor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.ws-status {
  padding: 0.25rem 0.625rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
}

.ws-on {
  background-color: #dcfce7;
  color: #16a34a;
}

.ws-off {
  background-color: #fee2e2;
  color: #dc2626;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: 1rem;
}

.status-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1.1rem;
  background-color: #ffffff;
  border-radius: 0.5rem;
  border: 1px solid #e2e8f0;
  border-left-width: 4px;
}

.accent-blue { border-left-color: #2563eb; }
.accent-green { border-left-color: #16a34a; }
.accent-amber { border-left-color: #d97706; }
.accent-red { border-left-color: #dc2626; }

.card-label {
  font-size: 0.8125rem;
  font-weight: 500;
  color: #64748b;
}

.card-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1e293b;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.section-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.robot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: 0.75rem;
}

.robot-card {
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  padding: 0.875rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.robot-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.robot-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1e293b;
}

.robot-meta {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  font-size: 0.75rem;
  color: #64748b;
}

.battery-bar {
  height: 0.375rem;
  background-color: #f1f5f9;
  border-radius: 0.1875rem;
  overflow: hidden;
}

.battery-fill {
  height: 100%;
  background-color: #16a34a;
  transition: width 0.3s;
}

.mission-monitor-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.mission-monitor-item {
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  padding: 0.75rem 0.875rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.mission-monitor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mission-monitor-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: #1e293b;
}

.mission-monitor-meta {
  display: flex;
  gap: 1.25rem;
  font-size: 0.75rem;
  color: #64748b;
  flex-wrap: wrap;
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
  transition: width 0.3s;
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
  font-size: 0.8125rem;
  align-self: flex-start;
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
