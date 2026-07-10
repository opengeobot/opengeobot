<!--
  Function: Mission create form page
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { createMission } from '@/api/mission'
import { listRobots } from '@/api/robot'
import type { Robot, SelectOption, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'READY' | 'FORBIDDEN' | 'ERROR'

const forbidden = computed(() => !authStore.permissions.includes('mission.mission.manage')
  && !authStore.permissions.includes('mission.mission.create')
  && !authStore.permissions.includes('mission.mission.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (errorMsg.value && !submitting.value) return 'ERROR'
  return 'READY'
})

const robots = ref<Robot[]>([])
const errorMsg = ref('')
const submitting = ref(false)

const form = reactive({
  name: '',
  description: '',
  robot_id: '',
  priority: 5,
  steps_json: '[{"action":"navigate","target":"home","parameters":{}}]'
})

const robotOptions = computed<SelectOption[]>(() =>
  robots.value.map((r) => ({ label: r.name, value: r.robot_id }))
)

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadRobots(): Promise<void> {
  try {
    const result = await listRobots({ page_number: 1, page_size: 100 })
    robots.value = result.items
  } catch {
    // optional
  }
}

async function handleSubmit(): Promise<void> {
  errorMsg.value = ''
  if (!form.name.trim() || !form.robot_id) {
    errorMsg.value = t('validation.required', { field: t('mission.name') })
    return
  }
  let steps: Array<{ action: string; target: string; parameters: Record<string, unknown> }>
  try {
    steps = JSON.parse(form.steps_json)
    if (!Array.isArray(steps)) throw new Error('not array')
  } catch {
    errorMsg.value = t('mission.invalid_steps')
    return
  }
  submitting.value = true
  try {
    const mission = await createMission({
      name: form.name.trim(),
      description: form.description,
      robot_id: form.robot_id,
      priority: Number(form.priority) || 5,
      steps
    })
    router.push(`/missions/${mission.mission_id || mission.id}`)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!forbidden.value) loadRobots()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('mission.create_title') }}</h1>
      <button class="btn btn-secondary" @click="router.push('/missions')">{{ t('common.cancel') }}</button>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('mission.forbidden') }}</div>

    <section v-else class="card">
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <div class="form-grid">
        <label class="field">
          <span>{{ t('mission.name') }}</span>
          <input v-model="form.name" class="input" type="text" />
        </label>
        <label class="field">
          <span>{{ t('mission.robot') }}</span>
          <select v-model="form.robot_id" class="input">
            <option value="">{{ t('common.select') }}</option>
            <option v-for="opt in robotOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
        <label class="field">
          <span>{{ t('mission.priority') }}</span>
          <input v-model.number="form.priority" class="input" type="number" min="1" max="10" />
        </label>
        <label class="field full">
          <span>{{ t('common.description') }}</span>
          <textarea v-model="form.description" class="input" rows="2" />
        </label>
        <label class="field full">
          <span>{{ t('mission.steps') }} (JSON)</span>
          <textarea v-model="form.steps_json" class="input mono" rows="8" />
        </label>
      </div>
      <div class="actions">
        <button class="btn btn-primary" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? t('common.loading') : t('common.save') }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.field { display: flex; flex-direction: column; gap: 0.375rem; font-size: 0.875rem; color: #475569; }
.field.full { grid-column: 1 / -1; }
.input { padding: 0.5rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 0.375rem; font-size: 0.875rem; }
.mono { font-family: ui-monospace, monospace; }
.actions { margin-top: 1.25rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
@media (max-width: 640px) { .form-grid { grid-template-columns: 1fr; } }
</style>
