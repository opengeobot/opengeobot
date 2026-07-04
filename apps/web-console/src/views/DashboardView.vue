<script setup lang="ts">
// Function: Dashboard view with status cards
// Time: 2026-07-03
// Author: AxeXie
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const cards = [
  { key: 'dashboard.robots', value: '—', accent: 'blue' },
  { key: 'dashboard.missions', value: '—', accent: 'green' },
  { key: 'dashboard.alarms', value: '—', accent: 'amber' },
  { key: 'dashboard.safety', value: '—', accent: 'red' }
]
</script>

<template>
  <div class="dashboard">
    <h1 class="dashboard-title">{{ t('dashboard.title') }}</h1>

    <div class="card-grid">
      <div
        v-for="card in cards"
        :key="card.key"
        class="status-card"
        :class="`accent-${card.accent}`"
      >
        <span class="card-label">{{ t(card.key) }}</span>
        <span class="card-value">{{ card.value }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
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
</style>
