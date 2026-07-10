<!--
  Function: Failure case browser (P-FAIL-001) — failed task cases with root cause
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listCases, getCase } from '@/api/memory'
import type {
  TaskCase,
  TaskCaseDetail,
  DataTableColumn,
  DataTablePagination,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

const rows = ref<TaskCase[]>([])
const loading = ref(false)
const errorMsg = ref('')
const forbidden = ref(false)
const pagination = ref<DataTablePagination>({ page_number: 1, page_size: 10, total: 0 })
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<TaskCaseDetail | null>(null)

const hasPermission = computed(() =>
  authStore.permissions.includes('memory.failure_case.read') ||
  authStore.permissions.includes('memory.memory.read')
)

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'case_id', title: t('memory.case_id') },
  { key: 'mission_id', title: t('memory.mission') },
  { key: 'robot_id', title: t('memory.robot') },
  { key: 'skill_id', title: t('memory.skill') },
  { key: 'error_message', title: t('memory.error') },
  { key: 'occurred_at', title: t('memory.occurred_at') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadRows(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listCases({
      page_number: pagination.value.page_number,
      page_size: pagination.value.page_size,
      result: 'FAILURE'
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

async function openDetail(row: TaskCase): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getCase(row.case_id)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    detailLoading.value = false
  }
}

function goImprovements(): void {
  router.push('/improvements')
}

onMounted(loadRows)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <h1>{{ t('nav.failure_cases') }}</h1>
      <div class="header-actions">
        <button type="button" class="btn" @click="goImprovements">{{ t('nav.improvements') }}</button>
        <button type="button" class="btn" @click="loadRows">{{ t('common.refresh') }}</button>
      </div>
    </header>

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
      <template #cell-error_message="{ row }">
        {{ String((row as unknown as TaskCase).error_message || '—') }}
      </template>
      <template #actions="{ row }">
        <button type="button" class="link-btn" @click="openDetail(row as unknown as TaskCase)">{{ t('common.view_detail') }}</button>
      </template>
    </DataTable>

    <ModalDialog :visible="detailVisible" :title="t('memory.failure_detail')" @close="detailVisible = false">
      <div v-if="detailLoading">{{ t('common.loading') }}</div>
      <div v-else-if="detail" class="detail-grid">
        <div><strong>{{ t('memory.case_id') }}</strong>: {{ detail.task_case.case_id }}</div>
        <div><strong>{{ t('memory.mission') }}</strong>: {{ detail.task_case.mission_id }}</div>
        <div><strong>{{ t('memory.robot') }}</strong>: {{ detail.task_case.robot_id }}</div>
        <div><strong>{{ t('memory.skill') }}</strong>: {{ detail.task_case.skill_id }}</div>
        <div v-if="detail.failure_case">
          <strong>{{ t('memory.failure_type') }}</strong>:
          <StatusTag :status="detail.failure_case.failure_type" />
        </div>
        <div v-if="detail.failure_case">
          <strong>{{ t('memory.root_cause') }}</strong>: {{ detail.failure_case.root_cause }}
        </div>
        <div v-if="detail.failure_case?.similar_cases?.length">
          <strong>{{ t('memory.similar_cases') }}</strong>:
          {{ detail.failure_case.similar_cases.join(', ') }}
        </div>
        <p class="hint">{{ t('memory.no_auto_apply_hint') }}</p>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { padding: 1.25rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.header-actions { display: flex; gap: 0.5rem; }
.btn { padding: 0.4rem 0.8rem; cursor: pointer; }
.link-btn { background: none; border: none; color: #0b6bcb; cursor: pointer; }
.state-msg { padding: 1rem; color: #555; }
.state-msg.error { color: #b00020; }
.detail-grid { display: grid; gap: 0.5rem; }
.hint { margin-top: 0.75rem; color: #666; font-size: 0.9rem; }
</style>
