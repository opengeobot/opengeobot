<!--
  Function: Edge gateway detail — status, heartbeat, certificate rotation
  Time: 2026-07-10
  Author: AxeXie
-->
<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  getEdgeGateway,
  activateEdgeGateway,
  revokeEdgeGateway,
  heartbeatEdgeGateway,
  rotateCertificate
} from '@/api/edge'
import type { EdgeGateway, FormField, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type ViewState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'ERROR'

const gatewayId = computed(() => String(route.params.gatewayId ?? ''))
const gateway = ref<EdgeGateway | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const forbidden = ref(false)

const hasPermission = computed(() => authStore.permissions.includes('edge.gateway.read'))

const viewState = computed<ViewState>(() => {
  if (forbidden.value) return 'FORBIDDEN'
  if (loading.value && !gateway.value) return 'LOADING'
  if (errorMsg.value && !gateway.value) return 'ERROR'
  return 'READY'
})

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
    gateway.value = await getEdgeGateway(gatewayId.value)
  } catch (err) {
    const problem = err as ProblemDetails
    if (problem.status === 403) forbidden.value = true
    else errorMsg.value = resolveError(problem)
  } finally {
    loading.value = false
  }
}

async function handleActivate(): Promise<void> {
  try {
    gateway.value = await activateEdgeGateway(gatewayId.value)
    successMsg.value = t('common.operation_success')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleRevoke(): Promise<void> {
  try {
    gateway.value = await revokeEdgeGateway(gatewayId.value)
    successMsg.value = t('common.operation_success')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleHeartbeat(): Promise<void> {
  try {
    gateway.value = await heartbeatEdgeGateway(gatewayId.value, {
      runtime_version: gateway.value?.runtime_version ?? undefined
    })
    successMsg.value = t('edge.heartbeat_ok')
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

const certVisible = ref(false)
const certModel = reactive<Record<string, unknown>>({})
const certFields = computed<FormField[]>(() => [
  { key: 'fingerprint', label: t('edge.fingerprint'), type: 'text', required: true },
  { key: 'expires_at', label: t('edge.cert_expires'), type: 'text', required: true, placeholder: '2027-01-01T00:00:00Z' }
])

async function handleRotate(values: Record<string, unknown>): Promise<void> {
  try {
    await rotateCertificate(gatewayId.value, {
      fingerprint: String(values.fingerprint),
      expires_at: String(values.expires_at)
    })
    certVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadDetail()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
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
      <h1 class="page-title">{{ t('edge.detail_title') }}</h1>
      <button class="btn btn-secondary" @click="router.push('/edge-gateways')">{{ t('common.cancel') }}</button>
    </div>

    <div v-if="viewState === 'FORBIDDEN'" class="alert alert-error">{{ t('edge.forbidden') }}</div>
    <p v-else-if="viewState === 'LOADING'" class="loading-text">{{ t('common.loading') }}</p>
    <p v-else-if="viewState === 'ERROR'" class="alert alert-error">{{ errorMsg }}</p>

    <template v-else-if="gateway">
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

      <section class="card">
        <div class="info-grid">
          <div><span class="label">{{ t('edge.name') }}</span><span>{{ gateway.name }}</span></div>
          <div><span class="label">{{ t('edge.gateway_id') }}</span><span class="mono">{{ gateway.gateway_id }}</span></div>
          <div><span class="label">{{ t('common.status') }}</span><StatusTag :status="gateway.status" type="enable-disable" /></div>
          <div><span class="label">{{ t('edge.org') }}</span><span>{{ gateway.org_id }}</span></div>
          <div><span class="label">{{ t('edge.runtime_version') }}</span><span>{{ gateway.runtime_version || '-' }}</span></div>
          <div><span class="label">{{ t('edge.bound_robot') }}</span><span>{{ gateway.bound_robot_id || '-' }}</span></div>
          <div><span class="label">{{ t('edge.fingerprint') }}</span><span class="mono">{{ gateway.certificate_fingerprint || '-' }}</span></div>
          <div><span class="label">{{ t('edge.cert_expires') }}</span><span>{{ gateway.certificate_expires_at || '-' }}</span></div>
          <div><span class="label">{{ t('edge.last_heartbeat') }}</span><span>{{ gateway.last_heartbeat_at || '-' }}</span></div>
        </div>

        <div class="actions">
          <button
            v-if="gateway.status === 'PENDING'"
            v-permission="'edge.gateway.manage'"
            class="btn btn-primary"
            @click="handleActivate"
          >{{ t('edge.activate') }}</button>
          <button
            v-if="gateway.status === 'ACTIVE'"
            v-permission="'edge.gateway.manage'"
            class="btn btn-secondary"
            @click="handleHeartbeat"
          >{{ t('edge.heartbeat') }}</button>
          <button
            v-if="gateway.status === 'ACTIVE'"
            v-permission="'edge.gateway.certificate.rotate'"
            class="btn btn-secondary"
            @click="certVisible = true"
          >{{ t('edge.rotate_cert') }}</button>
          <button
            v-if="gateway.status === 'ACTIVE'"
            v-permission="'edge.gateway.manage'"
            class="btn btn-danger"
            @click="handleRevoke"
          >{{ t('edge.revoke') }}</button>
        </div>
      </section>
    </template>

    <ModalDialog :visible="certVisible" :title="t('edge.rotate_cert')" :width="480" @close="certVisible = false">
      <FormBuilder :fields="certFields" :model-value="certModel" @submit="handleRotate" @cancel="certVisible = false" />
    </ModalDialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 1rem; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 1.5rem; font-weight: 700; color: #1e293b; margin: 0; }
.card { border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 1.25rem; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr)); gap: 1rem; }
.label { display: block; font-size: 0.75rem; color: #64748b; margin-bottom: 0.25rem; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8125rem; word-break: break-all; }
.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 1.25rem; }
.btn { padding: 0.5rem 1.25rem; border: none; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
.btn-primary { background-color: #2563eb; color: #fff; }
.btn-secondary { background-color: #f1f5f9; color: #475569; }
.btn-danger { background-color: #dc2626; color: #fff; }
.alert { padding: 0.625rem 0.875rem; border-radius: 0.375rem; font-size: 0.8125rem; }
.alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.alert-success { background-color: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }
.loading-text { color: #64748b; }
</style>
