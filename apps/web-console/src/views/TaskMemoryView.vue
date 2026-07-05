<!--
  Function: Task memory case browsing, failure analysis and improvement suggestion feedback page
  Time: 2026-07-05
  Author: AxeXie
-->
<script setup lang="ts">
import { onMounted, ref, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listCases, getCase, listSuggestions, submitFeedback } from '@/api/memory'
import type {
  DataTableColumn,
  DataTablePagination,
  TaskCase,
  TaskCaseDetail,
  ImprovementSuggestion,
  CaseResult,
  SuggestionStatus,
  TaskCaseListParams,
  SuggestionListParams
} from '@/types/api'

const { t } = useI18n()

// ---- Case list ----
const caseRows = ref<TaskCase[]>([])
const caseLoading = ref(false)
const casePagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })
const resultFilter = ref<CaseResult | ''>('')

const caseColumns = computed<DataTableColumn[]>(() => [
  { key: 'mission_id', title: t('memory.mission') },
  { key: 'robot_id', title: t('memory.robot') },
  { key: 'skill_id', title: t('memory.skill') },
  { key: 'result', title: t('memory.result') },
  { key: 'duration_ms', title: t('memory.duration') },
  { key: 'occurred_at', title: t('memory.occurred_at') }
])

async function loadCases() {
  caseLoading.value = true
  try {
    const params: TaskCaseListParams = {
      page_number: casePagination.value.page_number,
      page_size: casePagination.value.page_size,
      result: resultFilter.value || undefined
    }
    const result = await listCases(params)
    caseRows.value = result.items
    casePagination.value.total = result.total
  } finally {
    caseLoading.value = false
  }
}

function onCasePageChange(page: number) {
  casePagination.value.page_number = page
  loadCases()
}

function handleFilterChange() {
  casePagination.value.page_number = 1
  loadCases()
}

// ---- Case detail ----
const detailModalVisible = ref(false)
const detailLoading = ref(false)
const caseDetail = ref<TaskCaseDetail | null>(null)

async function viewDetail(row: TaskCase) {
  detailLoading.value = true
  detailModalVisible.value = true
  try {
    caseDetail.value = await getCase(row.case_id)
  } finally {
    detailLoading.value = false
  }
}

function formatContext(ctx: Record<string, unknown> | undefined): string {
  if (!ctx) return ''
  try {
    return JSON.stringify(ctx, null, 2)
  } catch {
    return String(ctx)
  }
}

// ---- Suggestions ----
const suggestionRows = ref<ImprovementSuggestion[]>([])
const suggestionLoading = ref(false)
const suggestionPagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })

const suggestionColumns = computed<DataTableColumn[]>(() => [
  { key: 'suggestion_text', title: t('memory.suggestion_text') },
  { key: 'confidence', title: t('memory.confidence') },
  { key: 'status', title: t('memory.suggestion_status') },
  { key: 'created_at', title: t('common.created_at') }
])

async function loadSuggestions() {
  suggestionLoading.value = true
  try {
    const params: SuggestionListParams = {
      page_number: suggestionPagination.value.page_number,
      page_size: suggestionPagination.value.page_size
    }
    const result = await listSuggestions(params)
    suggestionRows.value = result.items
    suggestionPagination.value.total = result.total
  } finally {
    suggestionLoading.value = false
  }
}

function onSuggestionPageChange(page: number) {
  suggestionPagination.value.page_number = page
  loadSuggestions()
}

// ---- Feedback ----
const feedbackModalVisible = ref(false)
const feedbackTarget = ref<ImprovementSuggestion | null>(null)
const feedbackText = ref('')

function openFeedback(row: ImprovementSuggestion) {
  feedbackTarget.value = row
  feedbackText.value = row.feedback ?? ''
  feedbackModalVisible.value = true
}

async function handleFeedbackSubmit() {
  if (!feedbackTarget.value) return
  await submitFeedback({
    suggestion_id: feedbackTarget.value.suggestion_id,
    feedback: feedbackText.value
  })
  feedbackModalVisible.value = false
  loadSuggestions()
}

onMounted(() => {
  loadCases()
  loadSuggestions()
})
</script>

<template>
  <div class="memory-management">
    <div class="page-header">
      <h2>{{ t('memory.title') }}</h2>
    </div>

    <section class="card">
      <div class="section-toolbar">
        <h3>{{ t('memory.cases') }}</h3>
        <div class="filter-bar">
          <label class="filter-label">{{ t('memory.filter_result') }}</label>
          <select v-model="resultFilter" class="form-input" @change="handleFilterChange">
            <option value="">{{ t('common.all') }}</option>
            <option value="SUCCESS">{{ t('status.memory.SUCCESS') }}</option>
            <option value="FAILURE">{{ t('status.memory.FAILURE') }}</option>
          </select>
        </div>
      </div>
      <DataTable
        :columns="caseColumns"
        :data="caseRows"
        :loading="caseLoading"
        :pagination="casePagination"
        @page-change="onCasePageChange"
      >
        <template #cell-result="{ row }">
          <StatusTag :status="row.result as string" type="memory" />
        </template>
        <template #actions="{ row }">
          <button class="btn-link" @click="viewDetail(row as unknown as TaskCase)">{{ t('memory.view_detail') }}</button>
        </template>
      </DataTable>
    </section>

    <section class="card">
      <h3>{{ t('memory.suggestions') }}</h3>
      <DataTable
        :columns="suggestionColumns"
        :data="suggestionRows"
        :loading="suggestionLoading"
        :pagination="suggestionPagination"
        @page-change="onSuggestionPageChange"
      >
        <template #cell-status="{ row }">
          <StatusTag :status="row.status as string" type="memory" />
        </template>
        <template #actions="{ row }">
          <button class="btn-link" @click="openFeedback(row as unknown as ImprovementSuggestion)">
            {{ t('memory.feedback') }}
          </button>
        </template>
      </DataTable>
    </section>

    <!-- Case detail -->
    <ModalDialog :visible="detailModalVisible" :title="t('memory.case_detail')" :width="640" @close="detailModalVisible = false">
      <p v-if="detailLoading" class="loading-text">{{ t('common.loading') }}</p>
      <template v-else-if="caseDetail">
        <div class="detail-section">
          <h4>{{ t('memory.cases') }}</h4>
          <dl class="detail-list">
            <div class="detail-row">
              <dt>{{ t('memory.mission') }}</dt><dd>{{ caseDetail.task_case.mission_id }}</dd>
            </div>
            <div class="detail-row">
              <dt>{{ t('memory.robot') }}</dt><dd>{{ caseDetail.task_case.robot_id }}</dd>
            </div>
            <div class="detail-row">
              <dt>{{ t('memory.skill') }}</dt><dd>{{ caseDetail.task_case.skill_id }}</dd>
            </div>
            <div class="detail-row">
              <dt>{{ t('memory.result') }}</dt>
              <dd><StatusTag :status="caseDetail.task_case.result" type="memory" /></dd>
            </div>
            <div class="detail-row">
              <dt>{{ t('memory.duration') }}</dt><dd>{{ caseDetail.task_case.duration_ms ?? '-' }}</dd>
            </div>
            <div class="detail-row">
              <dt>{{ t('memory.occurred_at') }}</dt><dd>{{ caseDetail.task_case.occurred_at }}</dd>
            </div>
            <div class="detail-row">
              <dt>{{ t('memory.trace_id') }}</dt><dd>{{ caseDetail.task_case.trace_id ?? '-' }}</dd>
            </div>
            <div v-if="caseDetail.task_case.error_message" class="detail-row">
              <dt>{{ t('memory.error_message') }}</dt><dd>{{ caseDetail.task_case.error_message }}</dd>
            </div>
            <div v-if="caseDetail.task_case.context" class="detail-row">
              <dt>{{ t('memory.context') }}</dt><dd><pre class="json-block">{{ formatContext(caseDetail.task_case.context) }}</pre></dd>
            </div>
          </dl>
        </div>

        <div v-if="caseDetail.failure_case" class="detail-section">
          <h4>{{ t('memory.failure_type') }}</h4>
          <dl class="detail-list">
            <div class="detail-row">
              <dt>{{ t('memory.failure_type') }}</dt><dd>{{ caseDetail.failure_case.failure_type }}</dd>
            </div>
            <div class="detail-row">
              <dt>{{ t('memory.root_cause') }}</dt><dd>{{ caseDetail.failure_case.root_cause }}</dd>
            </div>
            <div v-if="caseDetail.failure_case.similar_cases?.length" class="detail-row">
              <dt>{{ t('memory.similar_cases') }}</dt>
              <dd>{{ caseDetail.failure_case.similar_cases.join(', ') }}</dd>
            </div>
          </dl>
        </div>
      </template>
      <template #footer>
        <button class="btn btn-secondary" @click="detailModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>

    <!-- Feedback -->
    <ModalDialog :visible="feedbackModalVisible" :title="t('memory.submit_feedback')" :width="480" @close="feedbackModalVisible = false">
      <div class="feedback-form">
        <label class="form-label">{{ t('memory.feedback') }}</label>
        <textarea v-model="feedbackText" class="form-input form-textarea" :placeholder="t('memory.feedback_placeholder')" />
      </div>
      <template #footer>
        <button class="btn btn-primary" @click="handleFeedbackSubmit">{{ t('common.confirm') }}</button>
        <button class="btn btn-secondary" @click="feedbackModalVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.memory-management {
  padding: 1rem;
}
.page-header {
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
.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}
.section-toolbar h3 {
  margin: 0;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.filter-label {
  font-size: 0.875rem;
  color: #475569;
}
.form-input {
  padding: 0.4rem 0.6rem;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  font-size: 0.875rem;
}
.form-textarea {
  width: 100%;
  min-height: 100px;
  resize: vertical;
}
.form-label {
  display: block;
  font-size: 0.875rem;
  font-weight: 500;
  color: #334155;
  margin-bottom: 0.375rem;
}
.feedback-form {
  display: flex;
  flex-direction: column;
}
.detail-section {
  margin-bottom: 1rem;
}
.detail-section h4 {
  margin: 0 0 0.5rem;
  color: #1e293b;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 0.25rem;
}
.detail-list {
  margin: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.4rem;
}
.detail-row {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 0.5rem;
  font-size: 0.875rem;
}
.detail-row dt {
  color: #64748b;
}
.detail-row dd {
  margin: 0;
  color: #1e293b;
  word-break: break-word;
}
.json-block {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 0.5rem;
  font-size: 0.75rem;
  overflow-x: auto;
  margin: 0;
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
.btn-link {
  background: none;
  border: none;
  color: var(--color-primary, #2563eb);
  cursor: pointer;
  padding: 0.2rem 0.4rem;
  font-size: 0.875rem;
}
.loading-text {
  color: #64748b;
}
</style>
