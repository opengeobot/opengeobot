<!--
  Function: Skill detail with version history
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getSkill, getSkillVersions } from '@/api/skill'
import type { Skill, SkillVersion, DataTableColumn, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const skillId = computed(() => String(route.params.skillId ?? ''))
const skill = ref<Skill | null>(null)
const versions = ref<SkillVersion[]>([])
const loading = ref(false)
const errorMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() => authStore.permissions.includes('skill.skill.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !skill.value) return 'LOADING'
  if (errorMsg.value && !skill.value) return 'ERROR'
  return 'READY'
})

const versionColumns = computed<DataTableColumn[]>(() => [
  { key: 'version', title: t('common.version') },
  { key: 'status', title: t('common.status') },
  { key: 'change_log', title: t('skill.change_log') },
  { key: 'published_by', title: t('skill.published_by') },
  { key: 'published_at', title: t('common.created_at') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadDetail(): Promise<void> {
  if (!hasPermission.value) {
    forbidden.value = true
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    skill.value = await getSkill(skillId.value)
    try {
      versions.value = await getSkillVersions(skillId.value)
    } catch {
      versions.value = []
    }
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (hasPermission.value) loadDetail()
  else forbidden.value = true
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('skill.detail_title') }}</h1>
      <button class="btn btn-secondary" @click="router.push('/skills')">{{ t('common.cancel') }}</button>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('skill.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="skill">
      <section class="card">
        <div class="info-grid">
          <div><span class="label">{{ t('skill.name') }}</span><span>{{ skill.name }}</span></div>
          <div><span class="label">{{ t('skill.code') }}</span><span class="mono">{{ skill.skill_id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="skill.status" type="publish" /></div>
          <div><span class="label">{{ t('common.module') }}</span><span>{{ skill.module }}</span></div>
          <div><span class="label">{{ t('skill.current_version') }}</span><span>{{ skill.current_version }}</span></div>
          <div><span class="label">{{ t('common.description') }}</span><span>{{ skill.description || '-' }}</span></div>
        </div>
      </section>

      <section class="card">
        <h2 class="section-title">{{ t('skill.version_title') }}</h2>
        <DataTable :columns="versionColumns" :data="versions" :loading="false">
          <template #cell-status="{ row }">
            <StatusTag :status="row.status as string" type="publish" />
          </template>
        </DataTable>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); gap: 1rem; }
.label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.25rem; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; }
.section-title { font-size: 1rem; font-weight: 600; margin: 0 0 0.75rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.loading-text { color: #64748b; }
</style>
