<script setup lang="ts">
// Function: Mission management view with steps, execution control and templates
// Time: 2026-07-05
// Author: AxeXie
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listMissions,
  createMission,
  updateMission,
  startMission,
  pauseMission,
  resumeMission,
  cancelMission,
  getMission,
  listMissionTemplates,
  createMissionTemplate,
  submitApproval,
  approveMission,
  rejectMission
} from '@/api/mission'
import { listRobots } from '@/api/robot'
import type {
  Mission,
  MissionTemplate,
  MissionStep,
  Robot,
  SelectOption,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const missions = ref<Mission[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const wsConnected = ref(false)
let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const filters = reactive({
  keyword: '',
  status: '',
  robot_id: ''
})

const pagination = ref<DataTablePagination>({
  page_number: 1,
  page_size: 10,
  total: 0
})

const robots = ref<Robot[]>([])

const columns = computed<DataTableColumn[]>(() => [
  { key: 'name', title: t('mission.name'), sortable: true },
  { key: 'robot_name', title: t('mission.robot') },
  { key: 'status', title: t('common.status') },
  { key: 'priority', title: t('mission.priority') },
  { key: 'created_at', title: t('common.created_at'), sortable: true }
])

const statusFilterOptions = computed<SelectOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('status.task.pending'), value: 'pending' },
  { label: t('status.task.running'), value: 'running' },
  { label: t('status.task.paused'), value: 'paused' },
  { label: t('status.task.succeeded'), value: 'succeeded' },
  { label: t('status.task.failed'), value: 'failed' },
  { label: t('status.task.cancelled'), value: 'cancelled' }
])

const robotOptions = computed<SelectOption[]>(() =>
  robots.value.map((r) => ({ label: r.name, value: r.id }))
)

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadMissions(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listMissions({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      robot_id: filters.robot_id || undefined
    })
    missions.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

async function loadRobots(): Promise<void> {
  try {
    const result = await listRobots({ page_number: 1, page_size: 200 })
    robots.value = result.items
  } catch {
    // Optional dependency; ignore failure
  }
}

function handleSearch(): void {
  pagination.value.page_number = 1
  loadMissions()
}

function handleResetFilters(): void {
  filters.keyword = ''
  filters.status = ''
  filters.robot_id = ''
  pagination.value.page_number = 1
  loadMissions()
}

function handlePageChange(page: number): void {
  pagination.value.page_number = page
  loadMissions()
}

function handleSizeChange(size: number): void {
  pagination.value.page_size = size
  pagination.value.page_number = 1
  loadMissions()
}

// ---- Create / Edit ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = reactive<{
  id?: string
  name: string
  description: string
  robot_id: string
  priority: number
  steps: Array<{ action: string; target: string; parameters: string }>
}>({
  name: '',
  description: '',
  robot_id: '',
  priority: 1,
  steps: [{ action: '', target: '', parameters: '{}' }]
})

interface StepDraft {
  action: string
  target: string
  parameters: string
}

function blankStep(): StepDraft {
  return { action: '', target: '', parameters: '{}' }
}

function openCreate(): void {
  formMode.value = 'create'
  formModel.id = undefined
  formModel.name = ''
  formModel.description = ''
  formModel.robot_id = ''
  formModel.priority = 1
  formModel.steps = [blankStep()]
  formVisible.value = true
}

function openEdit(row: Mission): void {
  formMode.value = 'edit'
  formModel.id = row.id
  formModel.name = row.name
  formModel.description = row.description
  formModel.robot_id = row.robot_id
  formModel.priority = row.priority
  formModel.steps = (row.steps ?? []).map((s) => ({
    action: s.action,
    target: s.target,
    parameters: safeStringify(s.parameters)
  }))
  if (formModel.steps.length === 0) formModel.steps = [blankStep()]
  formVisible.value = true
}

function safeStringify(params: Record<string, unknown> | undefined): string {
  if (!params) return '{}'
  try {
    return JSON.stringify(params, null, 2)
  } catch {
    return '{}'
  }
}

function safeParseParams(text: string): Record<string, unknown> {
  try {
    return JSON.parse(text) as Record<string, unknown>
  } catch {
    return {}
  }
}

function addStep(): void {
  formModel.steps.push(blankStep())
}

function removeStep(index: number): void {
  if (formModel.steps.length > 1) {
    formModel.steps.splice(index, 1)
  }
}

async function handleFormSubmit(): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  if (!formModel.name || !formModel.robot_id) {
    errorMsg.value = t('validation.required', { field: t('mission.name') })
    return
  }
  const stepsPayload = formModel.steps
    .filter((s) => s.action)
    .map((s) => ({
      action: s.action,
      target: s.target,
      parameters: safeParseParams(s.parameters)
    }))
  try {
    if (formMode.value === 'create') {
      await createMission({
        name: formModel.name,
        description: formModel.description,
        robot_id: formModel.robot_id,
        priority: formModel.priority,
        steps: stepsPayload
      })
    } else if (formModel.id) {
      await updateMission(formModel.id, {
        name: formModel.name,
        description: formModel.description,
        robot_id: formModel.robot_id,
        priority: formModel.priority
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadMissions()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Detail with execution control ----

const detailVisible = ref(false)
const detail = ref<Mission | null>(null)
const detailLoading = ref(false)

async function openDetail(row: Mission): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  errorMsg.value = ''
  try {
    detail.value = await getMission(row.id)
  } catch (err) {
    detail.value = row
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    detailLoading.value = false
  }
}

function canControl(status: string): boolean {
  return ['pending', 'running', 'paused'].includes(status)
}

async function runControl(action: 'start' | 'pause' | 'resume' | 'cancel' | 'submit' | 'approve', id: string): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    let updated: Mission
    if (action === 'start') updated = await startMission(id)
    else if (action === 'pause') updated = await pauseMission(id)
    else if (action === 'resume') updated = await resumeMission(id)
    else if (action === 'cancel') updated = await cancelMission(id)
    else if (action === 'submit') updated = await submitApproval(id)
    else updated = await approveMission(id)
    detail.value = updated
    successMsg.value = t('common.operation_success')
    await loadMissions()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Reject with reason ----

const rejectVisible = ref(false)
const rejectTargetId = ref('')
const rejectReason = ref('')

function openReject(id: string): void {
  rejectTargetId.value = id
  rejectReason.value = ''
  rejectVisible.value = true
}

async function handleRejectConfirm(): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const updated = await rejectMission(rejectTargetId.value, { reason: rejectReason.value })
    if (detail.value && detail.value.id === rejectTargetId.value) detail.value = updated
    rejectVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadMissions()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Templates ----

const templateVisible = ref(false)
const templates = ref<MissionTemplate[]>([])
const templateLoading = ref(false)
const templateForm = reactive<{
  template_code: string
  template_name: string
  description: string
  steps: Array<{ action: string; target: string; parameters: string }>
}>({
  template_code: '',
  template_name: '',
  description: '',
  steps: [{ action: '', target: '', parameters: '{}' }]
})

async function openTemplates(): Promise<void> {
  templateVisible.value = true
  templateLoading.value = true
  errorMsg.value = ''
  try {
    const result = await listMissionTemplates({ page_number: 1, page_size: 100 })
    templates.value = result.items
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    templateLoading.value = false
  }
}

function resetTemplateForm(): void {
  templateForm.template_code = ''
  templateForm.template_name = ''
  templateForm.description = ''
  templateForm.steps = [{ action: '', target: '', parameters: '{}' }]
}

function addTemplateStep(): void {
  templateForm.steps.push({ action: '', target: '', parameters: '{}' })
}

function removeTemplateStep(index: number): void {
  if (templateForm.steps.length > 1) templateForm.steps.splice(index, 1)
}

async function handleCreateTemplate(): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await createMissionTemplate({
      template_code: templateForm.template_code,
      template_name: templateForm.template_name,
      description: templateForm.description,
      steps: templateForm.steps
        .filter((s) => s.action)
        .map((s) => ({
          action: s.action,
          target: s.target,
          parameters: safeParseParams(s.parameters)
        }))
    })
    successMsg.value = t('common.operation_success')
    resetTemplateForm()
    await openTemplates()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- WebSocket real-time updates ----

function buildWsUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const token = localStorage.getItem('token') ?? ''
  return `${proto}://${window.location.host}/ws/monitor?token=${encodeURIComponent(token)}`
}

function handleWsMessage(event: MessageEvent): void {
  let msg: { type: string; payload: Record<string, unknown> }
  try {
    msg = JSON.parse(event.data as string) as { type: string; payload: Record<string, unknown> }
  } catch {
    return
  }
  const payload = msg.payload
  const missionId = payload.mission_id as string | undefined
  if (!missionId) return

  const idx = missions.value.findIndex((m) => m.id === missionId)
  if (idx < 0) return
  const mission = missions.value[idx]
  const currentDetail = detail.value?.id === missionId ? detail.value : null
  const status = payload.status as string | undefined
  const currentStep = payload.current_step as number | undefined

  if (msg.type === 'mission.started') {
    mission.status = 'running'
    if (currentDetail) currentDetail.status = 'running'
  } else if (msg.type === 'mission.step_completed') {
    if (currentDetail?.steps) {
      const stepId = payload.step_id as string | undefined
      const stepIndex = payload.step_index as number | undefined
      const step = currentDetail.steps.find(
        (s) => (stepId ? s.id === stepId : typeof stepIndex === 'number' && s.step_index === stepIndex)
      )
      if (step) step.status = (payload.step_status as string) ?? 'completed'
    }
  } else if (msg.type === 'mission.completed') {
    mission.status = 'completed'
    if (currentDetail) currentDetail.status = 'completed'
    loadMissions()
  } else if (msg.type === 'mission.failed') {
    mission.status = 'failed'
    if (currentDetail) currentDetail.status = 'failed'
    errorMsg.value = (payload.error as string) || (payload.reason as string) || t('mission.execution_failed')
  } else if (msg.type === 'mission.progress') {
    if (status) {
      mission.status = status
      if (currentDetail) currentDetail.status = status
    }
    if (currentDetail?.steps && typeof currentStep === 'number') {
      currentDetail.steps.forEach((s, i) => {
        if (i < currentStep) s.status = 'completed'
        else if (i === currentStep) s.status = 'running'
      })
    }
  }
}

function connectWs(): void {
  try {
    ws = new WebSocket(buildWsUrl())
  } catch {
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    wsConnected.value = true
  }

  ws.onmessage = handleWsMessage

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

onMounted(() => {
  loadRobots()
  loadMissions()
  connectWs()
})

onUnmounted(() => {
  disconnectWs()
})
</script>

<template>
  <div class="mission-management">
    <div class="page-header">
      <h1 class="page-title">{{ t('mission.title') }}</h1>
      <span v-if="wsConnected" class="ws-status ws-online">●</span>
      <span v-else class="ws-status ws-offline">●</span>
    </div>

    <div class="toolbar">
      <input
        v-model="filters.keyword"
        class="filter-input"
        type="text"
        :placeholder="t('mission.name')"
        @keyup.enter="handleSearch"
      />
      <select v-model="filters.robot_id" class="filter-select">
        <option value="">{{ t('common.all') }}</option>
        <option v-for="r in robots" :key="r.id" :value="r.id">{{ r.name }}</option>
      </select>
      <select v-model="filters.status" class="filter-select">
        <option v-for="opt in statusFilterOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">{{ t('common.search') }}</button>
      <button class="btn btn-secondary" @click="handleResetFilters">{{ t('common.reset') }}</button>
      <button v-permission="'platform.mission.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('mission.create_title') }}
      </button>
      <button class="btn btn-secondary" @click="openTemplates">
        {{ t('mission.template_title') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <DataTable
      :columns="columns"
      :data="missions"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #cell-status="{ row }">
        <StatusTag :status="row.status as string" type="task" />
      </template>
      <template #actions="{ row }">
        <div class="action-buttons">
          <button class="btn-link" @click="openDetail(row as unknown as Mission)">
            {{ t('common.view_detail') }}
          </button>
          <button v-permission="'platform.mission.manage'" class="btn-link" @click="openEdit(row as unknown as Mission)">
            {{ t('common.edit') }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- Create / Edit mission -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'create' ? t('mission.create_title') : t('mission.edit_title')"
      :width="640"
      @close="formVisible = false"
    >
      <div class="mission-form">
        <div class="form-field">
          <label class="form-label">{{ t('mission.name') }}<span class="required-mark">*</span></label>
          <input v-model="formModel.name" class="form-input" type="text" />
        </div>
        <div class="form-field">
          <label class="form-label">{{ t('common.description') }}</label>
          <textarea v-model="formModel.description" class="form-input form-textarea" />
        </div>
        <div class="form-row">
          <div class="form-field">
            <label class="form-label">{{ t('mission.robot') }}<span class="required-mark">*</span></label>
            <select v-model="formModel.robot_id" class="form-input">
              <option value="">{{ t('common.select') }}</option>
              <option v-for="opt in robotOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="form-field">
            <label class="form-label">{{ t('mission.priority') }}</label>
            <input v-model.number="formModel.priority" class="form-input" type="number" min="0" />
          </div>
        </div>
        <div class="steps-section">
          <div class="steps-header">
            <span class="form-label">{{ t('mission.steps') }}</span>
            <button type="button" class="btn btn-secondary btn-sm" @click="addStep">+</button>
          </div>
          <div v-for="(step, index) in formModel.steps" :key="index" class="step-item">
            <div class="step-row">
              <span class="step-index">{{ index + 1 }}</span>
              <input v-model="step.action" class="form-input step-action" :placeholder="t('mission.step_action')" />
              <input v-model="step.target" class="form-input step-target" :placeholder="t('mission.step_target')" />
              <button v-if="formModel.steps.length > 1" type="button" class="btn-link btn-danger" @click="removeStep(index)">×</button>
            </div>
            <textarea v-model="step.parameters" class="form-input step-params" :placeholder="t('mission.step_params')" />
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-primary" @click="handleFormSubmit">{{ t('common.confirm') }}</button>
        <button class="btn btn-secondary" @click="formVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Mission detail with execution control -->
    <ModalDialog
      :visible="detailVisible"
      :title="t('mission.detail_title')"
      :width="640"
      @close="detailVisible = false"
    >
      <p v-if="detailLoading" class="loading-text">{{ t('common.loading') }}</p>
      <template v-else-if="detail">
        <div class="detail-header">
          <span class="detail-name">{{ detail.name }}</span>
          <StatusTag :status="detail.status" type="task" />
        </div>
        <p class="detail-desc">{{ detail.description }}</p>
        <div class="detail-meta">
          <span>{{ t('mission.robot') }}: {{ detail.robot_name || detail.robot_id }}</span>
          <span>{{ t('mission.priority') }}: {{ detail.priority }}</span>
          <span>{{ t('common.trace_id') }}: {{ detail.id }}</span>
        </div>
        <h4 class="steps-title">{{ t('mission.steps') }}</h4>
        <ol v-if="detail.steps && detail.steps.length > 0" class="step-detail-list">
          <li v-for="s in detail.steps" :key="s.id" class="step-detail-item">
            <div class="step-detail-row">
              <span class="step-action">{{ s.action }}</span>
              <StatusTag :status="s.status" type="task" />
            </div>
            <span class="step-target">{{ t('mission.step_target') }}: {{ s.target }}</span>
          </li>
        </ol>
        <div v-else class="empty-cell">{{ t('common.no_data') }}</div>

        <div class="control-buttons">
          <button
            v-if="canControl(detail.status)"
            v-permission="'platform.mission.manage'"
            class="btn btn-primary"
            :disabled="detail.status !== 'pending' && detail.status !== 'paused'"
            @click="runControl('start', detail.id)"
          >
            {{ t('mission.start') }}
          </button>
          <button
            v-if="canControl(detail.status)"
            v-permission="'platform.mission.manage'"
            class="btn btn-secondary"
            :disabled="detail.status !== 'running'"
            @click="runControl('pause', detail.id)"
          >
            {{ t('mission.pause') }}
          </button>
          <button
            v-if="canControl(detail.status)"
            v-permission="'platform.mission.manage'"
            class="btn btn-secondary"
            :disabled="detail.status !== 'paused'"
            @click="runControl('resume', detail.id)"
          >
            {{ t('mission.resume') }}
          </button>
          <button
            v-if="canControl(detail.status)"
            v-permission="'platform.mission.manage'"
            class="btn btn-danger"
            @click="runControl('cancel', detail.id)"
          >
            {{ t('mission.cancel') }}
          </button>
          <button
            v-if="detail.status === 'pending'"
            v-permission="'platform.mission.manage'"
            class="btn btn-secondary"
            @click="runControl('submit', detail.id)"
          >
            {{ t('mission.submit_approval') }}
          </button>
          <button
            v-if="detail.status === 'pending_approval'"
            v-permission="'platform.mission.manage'"
            class="btn btn-primary"
            @click="runControl('approve', detail.id)"
          >
            {{ t('mission.approve') }}
          </button>
          <button
            v-if="detail.status === 'pending_approval'"
            v-permission="'platform.mission.manage'"
            class="btn btn-danger"
            @click="openReject(detail.id)"
          >
            {{ t('mission.reject') }}
          </button>
        </div>
      </template>
      <template #footer>
        <button class="btn btn-secondary" @click="detailVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Reject reason -->
    <ModalDialog
      :visible="rejectVisible"
      :title="t('mission.reject')"
      :width="420"
      @close="rejectVisible = false"
    >
      <div class="form-field">
        <label class="form-label">{{ t('mission.reject_reason') }}</label>
        <textarea v-model="rejectReason" class="form-input form-textarea" />
      </div>
      <template #footer>
        <button class="btn btn-danger" @click="handleRejectConfirm">{{ t('common.confirm') }}</button>
        <button class="btn btn-secondary" @click="rejectVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Templates -->
    <ModalDialog
      :visible="templateVisible"
      :title="t('mission.template_title')"
      :width="720"
      @close="templateVisible = false"
    >
      <p v-if="templateLoading" class="loading-text">{{ t('common.loading') }}</p>
      <template v-else>
        <div class="template-list">
          <div v-if="templates.length === 0" class="empty-cell">{{ t('common.no_data') }}</div>
          <div v-for="tpl in templates" :key="tpl.id" class="template-item">
            <div class="template-header">
              <span class="template-name">{{ tpl.template_name }}</span>
              <span class="template-code">{{ tpl.template_code }}</span>
            </div>
            <p class="template-desc">{{ tpl.description }}</p>
            <span class="template-steps">{{ t('mission.steps') }}: {{ tpl.steps?.length ?? 0 }}</span>
          </div>
        </div>

        <div class="template-create">
          <h4 class="steps-title">{{ t('mission.create_template') }}</h4>
          <div class="form-field">
            <label class="form-label">{{ t('mission.template_name') }}</label>
            <input v-model="templateForm.template_name" class="form-input" type="text" />
          </div>
          <div class="form-field">
            <label class="form-label">{{ t('mission.template_code') }}</label>
            <input v-model="templateForm.template_code" class="form-input" type="text" />
          </div>
          <div class="form-field">
            <label class="form-label">{{ t('common.description') }}</label>
            <textarea v-model="templateForm.description" class="form-input form-textarea" />
          </div>
          <div class="steps-section">
            <div class="steps-header">
              <span class="form-label">{{ t('mission.steps') }}</span>
              <button type="button" class="btn btn-secondary btn-sm" @click="addTemplateStep">+</button>
            </div>
            <div v-for="(step, index) in templateForm.steps" :key="index" class="step-item">
              <div class="step-row">
                <span class="step-index">{{ index + 1 }}</span>
                <input v-model="step.action" class="form-input step-action" :placeholder="t('mission.step_action')" />
                <input v-model="step.target" class="form-input step-target" :placeholder="t('mission.step_target')" />
                <button v-if="templateForm.steps.length > 1" type="button" class="btn-link btn-danger" @click="removeTemplateStep(index)">×</button>
              </div>
              <textarea v-model="step.parameters" class="form-input step-params" :placeholder="t('mission.step_params')" />
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <button v-permission="'platform.mission.manage'" class="btn btn-primary" @click="handleCreateTemplate">
          {{ t('mission.create_template') }}
        </button>
        <button class="btn btn-secondary" @click="templateVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.mission-management {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.ws-status {
  font-size: 0.875rem;
  line-height: 1;
}

.ws-online {
  color: #16a34a;
}

.ws-offline {
  color: #dc2626;
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

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background-color: #f1f5f9;
  color: #475569;
}

.btn-secondary:hover {
  background-color: #e2e8f0;
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-danger {
  background-color: #dc2626;
  color: #ffffff;
}

.btn-danger:hover {
  background-color: #b91c1c;
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

.btn-link.btn-danger {
  color: #dc2626;
}

.mission-form {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.form-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #334155;
}

.required-mark {
  color: #dc2626;
  margin-left: 0.125rem;
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
  min-height: 4rem;
  resize: vertical;
}

.form-row {
  display: flex;
  gap: 0.75rem;
}

.form-row .form-field {
  flex: 1;
}

.steps-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.steps-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.step-item {
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.step-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.step-index {
  font-size: 0.75rem;
  font-weight: 700;
  color: #2563eb;
  min-width: 1.25rem;
}

.step-action {
  flex: 1;
}

.step-target {
  flex: 1;
}

.step-params {
  font-family: monospace;
  font-size: 0.75rem;
  min-height: 3rem;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.detail-name {
  font-size: 1.1rem;
  font-weight: 700;
  color: #1e293b;
}

.detail-desc {
  font-size: 0.875rem;
  color: #475569;
  margin: 0 0 0.5rem;
}

.detail-meta {
  display: flex;
  gap: 1.25rem;
  font-size: 0.75rem;
  color: #64748b;
  flex-wrap: wrap;
  margin-bottom: 0.75rem;
}

.steps-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #334155;
  margin: 0.5rem 0;
}

.step-detail-list {
  list-style: none;
  margin: 0 0 0.75rem;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.step-detail-item {
  padding: 0.5rem;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.step-detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.step-action {
  font-weight: 600;
  font-size: 0.8125rem;
  color: #1e293b;
}

.step-target {
  font-size: 0.75rem;
  color: #64748b;
}

.control-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.75rem;
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
}

.confirm-text {
  font-size: 0.875rem;
  color: #334155;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.template-item {
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  padding: 0.625rem;
}

.template-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.25rem;
}

.template-name {
  font-weight: 600;
  font-size: 0.875rem;
  color: #1e293b;
}

.template-code {
  font-family: monospace;
  font-size: 0.75rem;
  color: #2563eb;
}

.template-desc {
  font-size: 0.75rem;
  color: #64748b;
  margin: 0 0 0.25rem;
}

.template-steps {
  font-size: 0.75rem;
  color: #64748b;
}

.template-create {
  border-top: 1px solid #e2e8f0;
  padding-top: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
</style>
