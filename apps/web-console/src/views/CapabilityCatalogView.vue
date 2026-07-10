<!--
  Function: Capability catalog aggregated from skills and robots
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import { listSkills } from '@/api/skill'
import { listRobots } from '@/api/robot'
import type { DataTableColumn, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'ERROR'

interface CapabilityRow {
  code: string
  source: string
  skill_id?: string
  skill_name?: string
  robot_count: number
}

const rows = ref<CapabilityRow[]>([])
const loading = ref(false)
const errorMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() =>
  authStore.permissions.includes('skill.skill.read') ||
  authStore.permissions.includes('robot.robot.read')
)

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && rows.value.length === 0) return 'LOADING'
  if (errorMsg.value && rows.value.length === 0) return 'ERROR'
  if (rows.value.length === 0 && !loading.value) return 'EMPTY'
  return 'READY'
})

const columns = computed<DataTableColumn[]>(() => [
  { key: 'code', title: t('capability.code'), sortable: true },
  { key: 'skill_name', title: t('capability.skill') },
  { key: 'source', title: t('capability.source') },
  { key: 'robot_count', title: t('capability.robot_count') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadCatalog(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const map = new Map<string, CapabilityRow>()

    if (authStore.permissions.includes('skill.skill.read')) {
      const skills = await listSkills({ page_number: 1, page_size: 200 })
      for (const skill of skills.items) {
        const code = skill.name
        if (!code) continue
        map.set(code, {
          code,
          source: 'skill',
          skill_id: skill.skill_id,
          skill_name: skill.name,
          robot_count: 0
        })
      }
    }

    if (authStore.permissions.includes('robot.robot.read')) {
      const robots = await listRobots({ page_number: 1, page_size: 200 })
      for (const robot of robots.items) {
        for (const cap of robot.capabilities ?? []) {
          const capCode = typeof cap === 'string' ? cap : cap.capability_type
          if (!capCode) continue
          const existing = map.get(capCode)
          if (existing) {
            existing.robot_count += 1
            if (existing.source === 'skill') existing.source = 'skill+robot'
          } else {
            map.set(capCode, { code: capCode, source: 'robot', robot_count: 1 })
          }
        }
      }
    }

    rows.value = Array.from(map.values()).sort((a, b) => a.code.localeCompare(b.code))
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

function openSkill(row: CapabilityRow): void {
  if (row.skill_id) {
    router.push(`/skills/${row.skill_id}`)
  }
}

onMounted(() => {
  if (hasPermission.value) loadCatalog()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('capability.title') }}</h1>
    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('capability.forbidden') }}</div>
    <template v-else>
      <div class="toolbar">
        <button class="btn btn-secondary" @click="loadCatalog">{{ t('common.refresh') }}</button>
      </div>
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
      <p v-else-if="viewState === 'EMPTY'" class="empty-text">{{ t('common.no_data') }}</p>
      <DataTable
        v-if="viewState === 'READY' || rows.length > 0"
        :columns="columns"
        :data="rows"
        :loading="loading"
      >
        <template #actions="{ row }">
          <button
            v-if="(row as unknown as CapabilityRow).skill_id"
            class="btn-link"
            @click="openSkill(row as unknown as CapabilityRow)"
          >
            {{ t('capability.view_skill') }}
          </button>
        </template>
      </DataTable>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.toolbar { display: flex; gap: 0.5rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.btn-link { background: transparent; border: none; color: #2563eb; cursor: pointer; font-size: 0.8125rem; padding: 0; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.loading-text, .empty-text { color: #64748b; }
</style>
