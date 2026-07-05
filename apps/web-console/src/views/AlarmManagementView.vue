<!--
  Function: Alarm event, rule and notification channel management page
  Time: 2026-07-05
  Author: AxeXie
-->
<script setup lang="ts">
import { onMounted, ref, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listAlarms,
  listRules,
  createRule,
  updateRule,
  acknowledgeAlarm,
  resolveAlarm,
  listChannels,
  createChannel
} from '@/api/alarm'
import type {
  DataTableColumn,
  DataTablePagination,
  FormField,
  AlarmEvent,
  AlarmRule,
  NotificationChannel,
  AlarmSeverity,
  AlarmListParams,
  AlarmRuleListParams
} from '@/types/api'

const { t } = useI18n()

const activeTab = ref<'alarms' | 'rules' | 'channels'>('alarms')

// ---- Alarm events ----
const alarmRows = ref<AlarmEvent[]>([])
const alarmLoading = ref(false)
const alarmPagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const alarmColumns = computed<DataTableColumn[]>(() => [
  { key: 'severity', title: t('alarm.severity') },
  { key: 'source', title: t('alarm.source') },
  { key: 'message', title: t('alarm.message') },
  { key: 'status', title: t('common.status') },
  { key: 'triggered_at', title: t('alarm.triggered_at') }
])

async function loadAlarms() {
  alarmLoading.value = true
  try {
    const params: AlarmListParams = {
      page_number: alarmPagination.value.page_number,
      page_size: alarmPagination.value.page_size
    }
    const result = await listAlarms(params)
    alarmRows.value = result.items
    alarmPagination.value.total = result.total
  } finally {
    alarmLoading.value = false
  }
}

function onAlarmPageChange(page: number) {
  alarmPagination.value.page_number = page
  loadAlarms()
}

async function handleAcknowledge(row: AlarmEvent) {
  await acknowledgeAlarm(row.alarm_id)
  loadAlarms()
}

async function handleResolve(row: AlarmEvent) {
  await resolveAlarm(row.alarm_id)
  loadAlarms()
}

// ---- Alarm rules ----
const ruleRows = ref<AlarmRule[]>([])
const ruleLoading = ref(false)
const rulePagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const ruleColumns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('alarm.rule_name') },
  { key: 'source', title: t('alarm.source') },
  { key: 'metric', title: t('alarm.metric') },
  { key: 'condition', title: t('alarm.condition') },
  { key: 'threshold', title: t('alarm.threshold') },
  { key: 'severity', title: t('alarm.severity') },
  { key: 'enabled', title: t('alarm.enabled') }
])

async function loadRules() {
  ruleLoading.value = true
  try {
    const params: AlarmRuleListParams = {
      page_number: rulePagination.value.page_number,
      page_size: rulePagination.value.page_size
    }
    const result = await listRules(params)
    ruleRows.value = result.items
    rulePagination.value.total = result.total
  } finally {
    ruleLoading.value = false
  }
}

function onRulePageChange(page: number) {
  rulePagination.value.page_number = page
  loadRules()
}

// ---- Create / edit rule ----
const ruleModalVisible = ref(false)
const ruleMode = ref<'create' | 'edit'>('create')
const ruleTarget = ref<AlarmRule | null>(null)
const ruleModel = reactive<Record<string, unknown>>({})

const severityOptions = [
  { label: t('status.severity.CRITICAL'), value: 'CRITICAL' },
  { label: t('status.severity.HIGH'), value: 'HIGH' },
  { label: t('status.severity.MEDIUM'), value: 'MEDIUM' },
  { label: t('status.severity.LOW'), value: 'LOW' }
]

const ruleFields = computed<FormField[]>(() => [
  { key: 'name', label: t('alarm.rule_name'), type: 'text', required: true },
  { key: 'source', label: t('alarm.source'), type: 'text', required: true },
  { key: 'metric', label: t('alarm.metric'), type: 'text', required: true },
  { key: 'condition', label: t('alarm.condition'), type: 'text', required: true },
  { key: 'threshold', label: t('alarm.threshold'), type: 'number', required: true },
  { key: 'severity', label: t('alarm.severity'), type: 'select', options: severityOptions, required: true },
  {
    key: 'enabled',
    label: t('alarm.enabled'),
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 'true' },
      { label: t('common.disabled'), value: 'false' }
    ]
  }
])

function openCreateRule() {
  ruleMode.value = 'create'
  ruleTarget.value = null
  Object.assign(ruleModel, {
    name: '', source: '', metric: '', condition: '',
    threshold: '', severity: 'MEDIUM', enabled: 'true'
  })
  ruleModalVisible.value = true
}

function openEditRule(row: AlarmRule) {
  ruleMode.value = 'edit'
  ruleTarget.value = row
  Object.assign(ruleModel, {
    name: row.name,
    source: row.source,
    metric: row.metric,
    condition: row.condition,
    threshold: String(row.threshold),
    severity: row.severity,
    enabled: row.enabled ? 'true' : 'false'
  })
  ruleModalVisible.value = true
}

async function handleRuleSubmit(data: Record<string, unknown>) {
  const payload = {
    name: String(data.name),
    source: String(data.source),
    metric: String(data.metric),
    condition: String(data.condition),
    threshold: Number(data.threshold),
    severity: data.severity as AlarmSeverity,
    enabled: data.enabled === 'true'
  }
  if (ruleMode.value === 'create') {
    await createRule(payload)
  } else if (ruleTarget.value) {
    await updateRule(ruleTarget.value.rule_id, {
      name: payload.name,
      condition: payload.condition,
      threshold: payload.threshold,
      severity: payload.severity,
      enabled: payload.enabled
    })
  }
  ruleModalVisible.value = false
  loadRules()
}

// ---- Notification channels ----
const channelRows = ref<NotificationChannel[]>([])
const channelLoading = ref(false)

const channelColumns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('alarm.channel_name') },
  { key: 'type', title: t('alarm.channel_type') },
  { key: 'enabled', title: t('alarm.enabled') }
])

async function loadChannels() {
  channelLoading.value = true
  try {
    channelRows.value = await listChannels()
  } finally {
    channelLoading.value = false
  }
}

const channelModalVisible = ref(false)
const channelModel = reactive<Record<string, unknown>>({})

const channelFields = computed<FormField[]>(() => [
  { key: 'name', label: t('alarm.channel_name'), type: 'text', required: true },
  {
    key: 'type',
    label: t('alarm.channel_type'),
    type: 'select',
    required: true,
    options: [
      { label: 'in-app', value: 'in-app' },
      { label: 'webhook', value: 'webhook' },
      { label: 'email', value: 'email' }
    ]
  },
  { key: 'config', label: t('alarm.channel_config'), type: 'textarea' },
  {
    key: 'enabled',
    label: t('alarm.enabled'),
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 'true' },
      { label: t('common.disabled'), value: 'false' }
    ]
  }
])

function openCreateChannel() {
  Object.assign(channelModel, { name: '', type: 'in-app', config: '', enabled: 'true' })
  channelModalVisible.value = true
}

async function handleChannelSubmit(data: Record<string, unknown>) {
  let config: Record<string, unknown> | undefined
  if (data.config) {
    try {
      config = JSON.parse(String(data.config))
    } catch {
      config = undefined
    }
  }
  await createChannel({
    name: String(data.name),
    type: data.type as NotificationChannel['type'],
    config,
    enabled: data.enabled === 'true'
  })
  channelModalVisible.value = false
  loadChannels()
}

onMounted(() => {
  loadAlarms()
  loadRules()
  loadChannels()
})
</script>

<template>
  <div class="alarm-management">
    <div class="page-header">
      <h2>{{ t('alarm.title') }}</h2>
    </div>

    <div class="tabs">
      <button :class="['tab', { active: activeTab === 'alarms' }]" @click="activeTab = 'alarms'">
        {{ t('alarm.alarms_tab') }}
      </button>
      <button :class="['tab', { active: activeTab === 'rules' }]" @click="activeTab = 'rules'">
        {{ t('alarm.rules_tab') }}
      </button>
      <button :class="['tab', { active: activeTab === 'channels' }]" @click="activeTab = 'channels'">
        {{ t('alarm.channels_tab') }}
      </button>
    </div>

    <section v-show="activeTab === 'alarms'" class="card">
      <h3>{{ t('alarm.active_alarms') }}</h3>
      <DataTable
        :columns="alarmColumns"
        :data="alarmRows"
        :loading="alarmLoading"
        :pagination="alarmPagination"
        @page-change="onAlarmPageChange"
      >
        <template #cell-severity="{ row }">
          <StatusTag :status="row.severity as string" type="severity" />
        </template>
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="alarm" />
        </template>
        <template #actions="{ row }">
          <div class="action-buttons">
            <button
              v-if="row.status === 'ACTIVE'"
              class="btn-link"
              @click="handleAcknowledge(row as unknown as AlarmEvent)"
            >
              {{ t('alarm.acknowledge') }}
            </button>
            <button
              v-if="row.status !== 'RESOLVED'"
              class="btn-link"
              @click="handleResolve(row as unknown as AlarmEvent)"
            >
              {{ t('alarm.resolve') }}
            </button>
          </div>
        </template>
      </DataTable>
    </section>

    <section v-show="activeTab === 'rules'" class="card">
      <div class="section-toolbar">
        <h3>{{ t('alarm.rules_tab') }}</h3>
        <button class="btn btn-primary" @click="openCreateRule">{{ t('common.create') }}</button>
      </div>
      <DataTable
        :columns="ruleColumns"
        :data="ruleRows"
        :loading="ruleLoading"
        :pagination="rulePagination"
        @page-change="onRulePageChange"
      >
        <template #cell-severity="{ row }">
          <StatusTag :status="row.severity as string" type="severity" />
        </template>
        <template #actions="{ row }">
          <button class="btn-link" @click="openEditRule(row as unknown as AlarmRule)">{{ t('common.edit') }}</button>
        </template>
      </DataTable>
    </section>

    <section v-show="activeTab === 'channels'" class="card">
      <div class="section-toolbar">
        <h3>{{ t('alarm.channels_tab') }}</h3>
        <button class="btn btn-primary" @click="openCreateChannel">{{ t('common.create') }}</button>
      </div>
      <DataTable
        :columns="channelColumns"
        :data="channelRows"
        :loading="channelLoading"
      >
        <template #cell-enabled="{ row }">
          <StatusTag :status="Boolean(row.enabled) ? 'enabled' : 'disabled'" type="enable-disable" />
        </template>
      </DataTable>
    </section>

    <ModalDialog
      :visible="ruleModalVisible"
      :title="ruleMode === 'create' ? t('alarm.create_rule_title') : t('alarm.edit_rule_title')"
      :width="560"
      @close="ruleModalVisible = false"
    >
      <FormBuilder :fields="ruleFields" :model-value="ruleModel" @submit="handleRuleSubmit" @cancel="ruleModalVisible = false" />
    </ModalDialog>

    <ModalDialog
      :visible="channelModalVisible"
      :title="t('alarm.create_channel_title')"
      :width="520"
      @close="channelModalVisible = false"
    >
      <FormBuilder :fields="channelFields" :model-value="channelModel" @submit="handleChannelSubmit" @cancel="channelModalVisible = false" />
    </ModalDialog>
  </div>
</template>

<style scoped>
.alarm-management {
  padding: 1rem;
}
.page-header {
  margin-bottom: 1rem;
}
.page-header h2 {
  margin: 0;
}
.tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid #e2e8f0;
}
.tab {
  padding: 0.5rem 1rem;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.875rem;
  color: #64748b;
  border-bottom: 2px solid transparent;
}
.tab.active {
  color: var(--color-primary, #2563eb);
  border-bottom-color: var(--color-primary, #2563eb);
  font-weight: 600;
}
.card {
  background: var(--color-surface, #fff);
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}
.section-toolbar h3 {
  margin: 0;
}
.action-buttons {
  display: flex;
  gap: 0.5rem;
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
.btn-link {
  background: none;
  border: none;
  color: var(--color-primary, #2563eb);
  cursor: pointer;
  padding: 0.2rem 0.4rem;
  font-size: 0.875rem;
}
</style>
