<!--
  Function: Improvement suggestion approval (P-IMPROVE-001) — must not auto-apply motion
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listSuggestions, submitFeedback } from '@/api/memory'
import type {
  ImprovementSuggestion,
  SuggestionStatus,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const rows = ref<ImprovementSuggestion[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)
const statusFilter = ref<SuggestionStatus | ''>('PENDING')
const pagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })
const decideVisible = ref(false)
const decideTarget = ref<ImprovementSuggestion | null>(null)
const decideAction = ref<'ACCEPT' | 'REJECT'>('ACCEPT')
const feedbackText = ref('')

const canManage = computed(() =>
  authStore.permissions.includes('memory.improvement.manage') ||
  authStore.permissions.includes('memory.improvement.approve')
)
const canRead = computed(() =>
  canManage.value || authStore.permissions.includes('memory.failure_case.read')
)

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'suggestion_id', title: t('memory.suggestion_id') },
  { key: 'case_id', title: t('memory.case_id') },
  { key: 'suggestion_text', title: t('memory.suggestion') },
  { key: 'confidence', title: t('memory.confidence') },
  { key: 'status', title: t('common.status') },
  { key: 'created_at', title: t('common.created_at') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadRows(): Promise<void> {
  if (!canRead.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listSuggestions({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      status: statusFilter.value || undefined
    })
    rows.value = result.items
    pagination.value.total = result.total
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

function openDecide(row: ImprovementSuggestion, action: 'ACCEPT' | 'REJECT'): void {
  decideTarget.value = row
  decideAction.value = action
  feedbackText.value = ''
  decideVisible.value = true
}

async function confirmDecide(): Promise<void> {
  if (!decideTarget.value || !feedbackText.value.trim()) return
  try {
    await submitFeedback({
      suggestion_id: decideTarget.value.suggestion_id,
      feedback: feedbackText.value.trim(),
      decision: decideAction.value
    })
    successMsg.value = decideAction.value === 'ACCEPT'
      ? t('memory.accepted_pending_rollout')
      : t('memory.rejected')
    decideVisible.value = false
    await loadRows()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(loadRows)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <h1>{{ t('nav.improvements') }}</h1>
      <div class="header-actions">
        <select v-model="statusFilter" class="filter" @change="pagination.page_number = 1; loadRows()">
          <option value="">{{ t('common.all') }}</option>
          <option value="PENDING">PENDING</option>
          <option value="ACCEPTED">ACCEPTED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="APPLIED">APPLIED</option>
        </select>
        <button type="button" class="btn" @click="loadRows">{{ t('common.refresh') }}</button>
      </div>
    </header>

    <p class="policy">{{ t('memory.no_auto_apply_hint') }}</p>
    <p v-if="successMsg" class="state-msg ok">{{ successMsg }}</p>
    <p v-if="viewState === 'FORBIDDEN'" class="state-msg">{{ t('common.forbidden') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="state-msg error">{{ errorMsg }}</p>
    <p v-else-if="viewState === 'EMPTY'" class="state-msg">{{ t('common.no_data') }}</p>
    <p v-else-if="viewState === 'LOADING'" class="state-msg">{{ t('common.loading') }}</p>

    <DataTable
      v-else
      :columns="columns"
      :data="rows as unknown as Record<string, unknown>[]"
      :loading="loading"
      :pagination="pagination"
      @page-change="(p: number) => { pagination.page_number = p; loadRows() }"
      @size-change="(s: number) => { pagination.page_size = s; pagination.page_number = 1; loadRows() }"
    >
      <template #cell-status="{ row }">
        <StatusTag :status="(row as unknown as ImprovementSuggestion).status" />
      </template>
      <template #cell-confidence="{ row }">
        {{ ((row as unknown as ImprovementSuggestion).confidence * 100).toFixed(0) }}%
      </template>
      <template #actions="{ row }">
        <template v-if="canManage && (row as unknown as ImprovementSuggestion).status === 'PENDING'">
          <button type="button" class="link-btn" @click="openDecide(row as unknown as ImprovementSuggestion, 'ACCEPT')">{{ t('memory.accept') }}</button>
          <button type="button" class="link-btn danger" @click="openDecide(row as unknown as ImprovementSuggestion, 'REJECT')">{{ t('memory.reject') }}</button>
        </template>
      </template>
    </DataTable>

    <ModalDialog
      :visible="decideVisible"
      :title="decideAction === 'ACCEPT' ? t('memory.accept') : t('memory.reject')"
      @close="decideVisible = false"
    >
      <p class="policy">{{ t('memory.no_auto_apply_hint') }}</p>
      <textarea v-model="feedbackText" class="form-input" rows="4" :placeholder="t('memory.feedback_placeholder')" />
      <div class="modal-actions">
        <button type="button" class="btn" @click="decideVisible = false">{{ t('common.cancel') }}</button>
        <button type="button" class="btn primary" :disabled="!feedbackText.trim()" @click="confirmDecide">
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { padding: 1.25rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; }
.header-actions { display: flex; gap: 0.5rem; align-items: center; }
.btn { padding: 0.4rem 0.8rem; cursor: pointer; }
.btn.primary { background: #0b6bcb; color: #fff; border: none; }
.link-btn { background: none; border: none; color: #0b6bcb; cursor: pointer; margin-right: 0.5rem; }
.link-btn.danger { color: #b00020; }
.state-msg { padding: 0.5rem 0; color: #555; }
.state-msg.error { color: #b00020; }
.state-msg.ok { color: #1b7f3a; }
.policy { color: #666; font-size: 0.9rem; margin-bottom: 0.75rem; }
.filter { padding: 0.35rem; }
.form-input { width: 100%; padding: 0.5rem; }
.modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 0.75rem; }
</style>
