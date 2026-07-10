<script setup lang="ts">
// Function: Dashboard view with real status cards from ops and safety APIs
// Time: 2026-07-03
// Author: AxeXie
import { onMounted, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { getDashboard } from '@/api/ops'
import { getSafetyState } from '@/api/safety'
import type { OpsDashboard, SafetyState, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()

const dashboard = ref<OpsDashboard | null>(null)
const safetyState = ref<SafetyState | null>(null)
const loading = ref(false)
const errorMsg = ref('')

const robotStats = computed(() => dashboard.value?.robot_stats)
const missionStats = computed(() => dashboard.value?.mission_stats)
const alarmStats = computed(() => dashboard.value?.alarm_stats)
const eStopped = computed<boolean>(() => safetyState.value?.state === 'EMERGENCY_STOPPED')

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadAll(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const [dash, safety] = await Promise.all([
      getDashboard(),
      getSafetyState()
    ])
    dashboard.value = dash
    safetyState.value = safety
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h1 class="dashboard-title">{{ t('dashboard.title') }}</h1>
      <button class="btn btn-primary" :disabled="loading" @click="loadAll">
        {{ t('common.refresh') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="loading" class="loading-text">{{ t('common.loading') }}</p>

    <template v-else>
      <!-- Robot status card -->
      <div class="card-grid">
        <div class="status-card accent-blue">
          <span class="card-label">{{ t('dashboard.robots') }}</span>
          <span class="card-value">{{ robotStats?.total ?? '—' }}</span>
          <div v-if="robotStats" class="card-breakdown">
            <span class="breakdown-item breakdown-online">
              {{ t('dashboard.online') }}: {{ robotStats.online }}
            </span>
            <span class="breakdown-item breakdown-offline">
              {{ t('dashboard.offline') }}: {{ robotStats.offline }}
            </span>
            <span class="breakdown-item breakdown-busy">
              {{ t('dashboard.busy') }}: {{ robotStats.busy }}
            </span>
            <span class="breakdown-item breakdown-error">
              {{ t('dashboard.error') }}: {{ robotStats.error }}
            </span>
          </div>
        </div>

        <!-- Mission status card -->
        <div class="status-card accent-green">
          <span class="card-label">{{ t('dashboard.missions') }}</span>
          <span class="card-value">{{ missionStats?.active ?? '—' }}</span>
          <div v-if="missionStats" class="card-breakdown">
            <span class="breakdown-item">
              {{ t('common.total') }}: {{ missionStats.total }}
            </span>
            <span class="breakdown-item breakdown-online">
              {{ t('dashboard.completed') }}: {{ missionStats.completed }}
            </span>
            <span class="breakdown-item breakdown-error">
              {{ t('dashboard.failed') }}: {{ missionStats.failed }}
            </span>
          </div>
        </div>

        <!-- Alarm summary card -->
        <div class="status-card accent-amber">
          <span class="card-label">{{ t('dashboard.alarms') }}</span>
          <span class="card-value">{{ alarmStats?.active ?? '—' }}</span>
          <div v-if="alarmStats" class="card-breakdown">
            <span class="breakdown-item breakdown-busy">
              {{ t('dashboard.acknowledged') }}: {{ alarmStats.acknowledged }}
            </span>
            <span class="breakdown-item breakdown-online">
              {{ t('dashboard.resolved') }}: {{ alarmStats.resolved }}
            </span>
          </div>
        </div>

        <!-- Safety status card -->
        <div class="status-card" :class="eStopped ? 'accent-red' : 'accent-green'">
          <span class="card-label">{{ t('dashboard.safety') }}</span>
          <span class="card-value">
            {{ eStopped ? t('dashboard.safety_emergency') : t('dashboard.safety_normal') }}
          </span>
          <div v-if="safetyState?.locked" class="card-breakdown">
            <span class="breakdown-item breakdown-error">
              {{ t('safety.locked') }}
            </span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dashboard-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 1rem;
}

.status-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1.25rem;
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
  font-size: 0.875rem;
  font-weight: 500;
  color: #64748b;
}

.card-value {
  font-size: 2rem;
  font-weight: 700;
  color: #1e293b;
}

.card-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  margin-top: 0.25rem;
}

.breakdown-item {
  font-size: 0.75rem;
  color: #64748b;
}

.breakdown-online { color: #16a34a; }
.breakdown-offline { color: #94a3b8; }
.breakdown-busy { color: #2563eb; }
.breakdown-error { color: #dc2626; }

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

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
</style>
