<script setup lang="ts">
// Function: Organization management view with tree, detail panel and CRUD
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listOrgs, createOrg, updateOrg, deleteOrg } from '@/api/org'
import type { Org, FormField, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()

const tree = ref<Org[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

interface FlatNode {
  org: Org
  depth: number
}

function flatten(nodes: Org[], depth = 0): FlatNode[] {
  const result: FlatNode[] = []
  for (const node of nodes) {
    result.push({ org: node, depth })
    if (node.children && node.children.length > 0) {
      result.push(...flatten(node.children, depth + 1))
    }
  }
  return result
}

const flatNodes = computed<FlatNode[]>(() => flatten(tree.value))

const selectedOrg = ref<Org | null>(null)

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadOrgs(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    tree.value = await listOrgs()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function selectNode(org: Org): void {
  selectedOrg.value = org
}

// ---- Create / edit modal ----

const formVisible = ref(false)
const formMode = ref<'create' | 'edit' | 'addChild'>('create')
const formModel = reactive<Record<string, unknown>>({})
const formSaving = ref(false)

const formFields = computed<FormField[]>(() => {
  const fields: FormField[] = [
    { key: 'org_code', label: t('org.org_code'), type: 'text', required: true },
    { key: 'org_name', label: t('org.org_name'), type: 'text', required: true },
    { key: 'description', label: t('common.description'), type: 'textarea' }
  ]
  if (formMode.value === 'edit') {
    fields.push({
      key: 'status',
      label: t('common.status'),
      type: 'select',
      options: [
        { label: t('status.enable-disable.enabled'), value: 'enabled' },
        { label: t('status.enable-disable.disabled'), value: 'disabled' }
      ]
    })
  }
  return fields
})

function openCreate(): void {
  formMode.value = 'create'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModel.parent_id = null
  formVisible.value = true
}

function openEdit(): void {
  if (!selectedOrg.value) return
  formMode.value = 'edit'
  formModel.id = selectedOrg.value.id
  formModel.org_code = selectedOrg.value.org_code
  formModel.org_name = selectedOrg.value.org_name
  formModel.description = selectedOrg.value.description
  formModel.status = selectedOrg.value.status
  formVisible.value = true
}

function openAddChild(): void {
  if (!selectedOrg.value) return
  formMode.value = 'addChild'
  Object.keys(formModel).forEach((k) => delete formModel[k])
  formModel.parent_id = selectedOrg.value.id
  formModel.parent_name = selectedOrg.value.org_name
  formVisible.value = true
}

async function handleFormSubmit(data: Record<string, unknown>): Promise<void> {
  formSaving.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (formMode.value === 'edit') {
      await updateOrg(String(formModel.id), {
        org_name: String(data.org_name ?? ''),
        description: String(data.description ?? ''),
        status: String(data.status ?? 'enabled')
      })
    } else {
      await createOrg({
        org_code: String(data.org_code),
        org_name: String(data.org_name),
        parent_id: formModel.parent_id ? String(formModel.parent_id) : null,
        description: String(data.description ?? '')
      })
    }
    formVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadOrgs()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    formSaving.value = false
  }
}

// ---- Delete confirm ----

const deleteVisible = ref(false)
const deleteTarget = ref<Org | null>(null)

function openDelete(): void {
  if (!selectedOrg.value) return
  deleteTarget.value = selectedOrg.value
  deleteVisible.value = true
}

async function handleDeleteConfirm(): Promise<void> {
  if (!deleteTarget.value) return
  errorMsg.value = ''
  try {
    await deleteOrg(deleteTarget.value.id)
    deleteVisible.value = false
    selectedOrg.value = null
    successMsg.value = t('common.operation_success')
    await loadOrgs()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadOrgs()
})
</script>

<template>
  <div class="org-management">
    <h1 class="page-title">{{ t('org.title') }}</h1>

    <div class="toolbar">
      <button v-permission="'platform.org.manage'" class="btn btn-primary" @click="openCreate">
        {{ t('common.create') }}
      </button>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <div class="org-layout">
      <div class="org-tree-panel">
        <p v-if="loading" class="loading-text">{{ t('common.loading') }}</p>
        <ul v-else class="org-tree">
          <li
            v-for="node in flatNodes"
            :key="node.org.id"
            class="org-tree-node"
            :class="{ 'node-selected': selectedOrg?.id === node.org.id }"
            :style="{ paddingLeft: 0.5 + node.depth * 1.25 + 'rem' }"
            @click="selectNode(node.org)"
          >
            <span class="node-icon">{{ node.org.children && node.org.children.length ? '▾' : '•' }}</span>
            <span class="node-name">{{ node.org.org_name }}</span>
            <span class="node-code">{{ node.org.org_code }}</span>
          </li>
        </ul>
      </div>

      <div class="org-detail-panel">
        <div v-if="!selectedOrg" class="empty-detail">
          {{ t('common.no_data') }}
        </div>
        <div v-else class="detail-content">
          <h2 class="detail-title">{{ selectedOrg.org_name }}</h2>
          <div class="detail-grid">
            <div class="detail-field">
              <span class="field-label">{{ t('org.org_code') }}</span>
              <span class="field-value">{{ selectedOrg.org_code }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">{{ t('common.status') }}</span>
              <StatusTag :status="selectedOrg.status" type="enable-disable" />
            </div>
            <div class="detail-field detail-field-wide">
              <span class="field-label">{{ t('common.description') }}</span>
              <span class="field-value">{{ selectedOrg.description || '—' }}</span>
            </div>
          </div>

          <div v-permission="'platform.org.manage'" class="detail-actions">
            <button class="btn btn-secondary" @click="openEdit">{{ t('common.edit') }}</button>
            <button class="btn btn-secondary" @click="openAddChild">{{ t('common.add_child') }}</button>
            <button class="btn btn-danger" @click="openDelete">{{ t('common.delete') }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create / Edit / Add child -->
    <ModalDialog
      :visible="formVisible"
      :title="formMode === 'edit' ? t('org.edit_title') : formMode === 'addChild' ? t('org.add_child_title') : t('org.create_title')"
      :width="480"
      @close="formVisible = false"
    >
      <FormBuilder
        :fields="formFields"
        :model-value="formModel"
        @submit="handleFormSubmit"
        @cancel="formVisible = false"
      />
    </ModalDialog>

    <!-- Delete confirm -->
    <ModalDialog
      :visible="deleteVisible"
      :title="t('common.delete')"
      :width="400"
      @close="deleteVisible = false"
    >
      <p class="confirm-text">{{ t('common.confirm_delete') }}</p>
      <template #footer>
        <button class="btn btn-danger" @click="handleDeleteConfirm">{{ t('common.confirm') }}</button>
        <button class="btn btn-secondary" @click="deleteVisible = false">{{ t('common.cancel') }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.org-management {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.toolbar {
  display: flex;
  gap: 0.5rem;
}

.org-layout {
  display: flex;
  gap: 1rem;
  min-height: 24rem;
}

.org-tree-panel {
  width: 18rem;
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  overflow-y: auto;
  flex-shrink: 0;
}

.org-tree {
  list-style: none;
  margin: 0;
  padding: 0.5rem 0;
}

.org-tree-node {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  font-size: 0.875rem;
  color: #1e293b;
  white-space: nowrap;
}

.org-tree-node:hover {
  background-color: #f1f5f9;
}

.node-selected {
  background-color: #dbeafe;
  color: #1d4ed8;
  font-weight: 600;
}

.node-icon {
  color: #94a3b8;
  font-size: 0.75rem;
  width: 0.875rem;
}

.node-code {
  font-size: 0.75rem;
  color: #94a3b8;
  margin-left: auto;
}

.org-detail-panel {
  flex: 1;
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  padding: 1.25rem;
}

.empty-detail {
  color: #94a3b8;
  font-size: 0.875rem;
  text-align: center;
  padding: 3rem 0;
}

.detail-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 1rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-field-wide {
  grid-column: span 2;
}

.field-label {
  font-size: 0.8125rem;
  color: #64748b;
  font-weight: 500;
}

.field-value {
  font-size: 0.875rem;
  color: #1e293b;
}

.detail-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.btn {
  padding: 0.5rem 1.25rem;
  border: none;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background-color: #2563eb;
  color: #ffffff;
}

.btn-primary:hover {
  background-color: #1d4ed8;
}

.btn-secondary {
  background-color: #f1f5f9;
  color: #475569;
}

.btn-secondary:hover {
  background-color: #e2e8f0;
}

.btn-danger {
  background-color: #fee2e2;
  color: #dc2626;
}

.btn-danger:hover {
  background-color: #fecaca;
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

.loading-text {
  color: #64748b;
  font-size: 0.875rem;
  padding: 1rem;
}

.confirm-text {
  font-size: 0.875rem;
  color: #334155;
}
</style>
