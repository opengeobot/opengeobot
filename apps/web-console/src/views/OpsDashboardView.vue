<!--
  Function: Operations dashboard with system health, robot/mission/alarm stats and capacity forecast
  Time: 2026-07-05
  Author: AxeXie
-->
<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import StatusTag from '@/components/StatusTag.vue'
import { getDashboard, getHealth, getCapacity, generateReport } from '@/api/ops'
import type {
  OpsDashboard,
  HealthCheck,
  CapacityForecast,
  ReportType,
  ReportRecord
} from '@/types/api'

const { t } = useI18n()

const dashboard = ref<OpsDashboard | null>(null)
const healthChecks = ref<HealthCheck[]>([])
const capacity = ref<CapacityForecast[]>([])
const loading = ref(false)
const reportLoading = ref(false)

const robotStats = computed(() => dashboard.value?.robot_stats)
const missionStats = computed(() => dashboard.value?.mission_stats)
const alarmStats = computed(() => dashboard.value?.alarm_stats)
const overallHealth = computed(() => dashboard.value?.system_health.overall ?? 'UNHEALTHY')

async function loadAll() {
  loading.value = true
  try {
    const [dash, health, cap] = await Promise.all([
      getDashboard(),
      getHealth(),
      getCapacity()
    ])
    dashboard.value = dash
    healthChecks.value = health
    capacity.value = cap
  } finally {
    loading.value = false
  }
}

async function handleGenerateReport(type: ReportType) {
  reportLoading.value = true
  try {
    const report: ReportRecord = await generateReport(type)
    // Surface the generated report period to the user via a lightweight alert
    alert(`${t('ops.generate_report')}: ${report.report_type}\n${report.period_start} ~ ${report.period_end}`)
  } finally {
    reportLoading.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="ops-dashboard">
    <div class="page-header">
      <h2>{{ t('ops.title') }}</h2>
      <button class="btn btn-primary" :disabled="loading" @click="loadAll">{{ t('common.refresh') }}</button>
    </div>

    <p v-if="loading" class="loading-text">{{ t('common.loading') }}</p>

    <template v-else>
      <!-- System health -->
      <section class="card">
        <div class="section-toolbar">
          <h3>{{ t('ops.system_health') }}</h3>
          <StatusTag :status="overallHealth" type="health" />
        </div>
        <div class="health-grid">
          <div v-for="check in healthChecks" :key="check.component" class="health-card">
            <div class="health-name">{{ check.component }}</div>
            <StatusTag :status="check.status" type="health" />
            <div v-if="check.latency_ms !== undefined" class="health-meta">
              {{ t('ops.latency') }}: {{ check.latency_ms }}
            </div>
            <div class="health-meta">{{ t('ops.last_check_at') }}: {{ check.last_check_at }}</div>
          </div>
        </div>
      </section>

      <!-- Robot stats -->
      <section class="card">
        <h3>{{ t('ops.robot_stats') }}</h3>
        <div class="stat-grid" v-if="robotStats">
          <div class="stat-item">
            <span class="stat-value">{{ robotStats.total }}</span>
            <span class="stat-label">{{ t('ops.total_robots') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-online">{{ robotStats.online }}</span>
            <span class="stat-label">{{ t('ops.online') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-offline">{{ robotStats.offline }}</span>
            <span class="stat-label">{{ t('ops.offline') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-busy">{{ robotStats.busy }}</span>
            <span class="stat-label">{{ t('ops.busy') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-error">{{ robotStats.error }}</span>
            <span class="stat-label">{{ t('ops.error') }}</span>
          </div>
        </div>
      </section>

      <!-- Mission stats -->
      <section class="card">
        <h3>{{ t('ops.mission_stats') }}</h3>
        <div class="stat-grid" v-if="missionStats">
          <div class="stat-item">
            <span class="stat-value">{{ missionStats.total }}</span>
            <span class="stat-label">{{ t('common.total') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-busy">{{ missionStats.active }}</span>
            <span class="stat-label">{{ t('ops.active') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-online">{{ missionStats.completed }}</span>
            <span class="stat-label">{{ t('ops.completed') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-error">{{ missionStats.failed }}</span>
            <span class="stat-label">{{ t('ops.failed') }}</span>
          </div>
        </div>
      </section>

      <!-- Alarm stats -->
      <section class="card">
        <h3>{{ t('ops.alarm_stats') }}</h3>
        <div class="stat-grid" v-if="alarmStats">
          <div class="stat-item">
            <span class="stat-value stat-error">{{ alarmStats.active }}</span>
            <span class="stat-label">{{ t('ops.active_alarms') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-busy">{{ alarmStats.acknowledged }}</span>
            <span class="stat-label">{{ t('ops.acknowledged') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-online">{{ alarmStats.resolved }}</span>
            <span class="stat-label">{{ t('ops.resolved') }}</span>
          </div>
        </div>
      </section>

      <!-- Capacity forecast -->
      <section class="card">
        <h3>{{ t('ops.capacity_forecast') }}</h3>
        <table class="capacity-table">
          <thead>
            <tr>
              <th>{{ t('ops.resource') }}</th>
              <th>{{ t('ops.current_usage') }}</th>
              <th>{{ t('ops.projected_usage') }}</th>
              <th>{{ t('ops.threshold') }}</th>
              <th>{{ t('ops.alert') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="capacity.length === 0">
              <td colspan="5" class="empty-cell">{{ t('common.no_data') }}</td>
            </tr>
            <tr v-for="item in capacity" :key="item.resource">
              <td>{{ item.resource }}</td>
              <td>{{ item.current_usage }}{{ item.unit ? ' ' + item.unit : '' }}</td>
              <td>{{ item.projected_usage }}{{ item.unit ? ' ' + item.unit : '' }}</td>
              <td>{{ item.threshold }}{{ item.unit ? ' ' + item.unit : '' }}</td>
              <td>
                <span :class="['alert-badge', item.alert ? 'alert-on' : 'alert-off']">
                  {{ item.alert ? t('common.enabled') : t('common.disabled') }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Reports -->
      <section class="card">
        <h3>{{ t('ops.generate_report') }}</h3>
        <div class="report-buttons">
          <button class="btn btn-secondary" :disabled="reportLoading" @click="handleGenerateReport('daily')">
            {{ t('ops.report_daily') }}
          </button>
          <button class="btn btn-secondary" :disabled="reportLoading" @click="handleGenerateReport('weekly')">
            {{ t('ops.report_weekly') }}
          </button>
          <button class="btn btn-secondary" :disabled="reportLoading" @click="handleGenerateReport('monthly')">
            {{ t('ops.report_monthly') }}
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.ops-dashboard {
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
.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 0.75rem;
}
.health-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.health-name {
  font-weight: 600;
  color: #1e293b;
}
.health-meta {
  font-size: 0.75rem;
  color: #64748b;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 0.75rem;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1rem;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}
.stat-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1e293b;
}
.stat-online { color: #16a34a; }
.stat-offline { color: #94a3b8; }
.stat-busy { color: #2563eb; }
.stat-error { color: #dc2626; }
.stat-label {
  font-size: 0.75rem;
  color: #64748b;
  margin-top: 0.25rem;
}
.capacity-table {
  width: 100%;
  border-collapse: collapse;
}
.capacity-table th,
.capacity-table td {
  text-align: left;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.875rem;
}
.alert-badge {
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
}
.alert-on {
  background: #fef2f2;
  color: #dc2626;
}
.alert-off {
  background: #f0fdf4;
  color: #16a34a;
}
.empty-cell {
  text-align: center;
  color: #94a3b8;
}
.report-buttons {
  display: flex;
  gap: 0.75rem;
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
.loading-text {
  color: #64748b;
}
</style>
