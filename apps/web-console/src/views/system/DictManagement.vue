<script setup lang="ts">
// Function: Dictionary management view with type list, items panel and publish
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import FormBuilder from '@/components/FormBuilder.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  listDictTypes,
  createDictType,
  updateDictType,
  deleteDictType,
  listDictItems,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  publishDictType
} from '@/api/dict'
import type {
  DictType,
  DictItem,
  FormField,
  DataTableColumn,
  ProblemDetails
} from '@/types/api'

const { t, te } = useI18n()

const types = ref<DictType[]>([])
const items = ref<DictItem[]>([])
const selectedType = ref<DictType | null>(null)
const loading = ref(false)
const itemLoading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const typeColumns = computed<DataTableColumn[]>(() => [
  { key: 'type_code', title: t('dict.type_code') },
  { key: 'type_name', title: t('dict.type_name') },
  { key: 'status', title: t('common.status') },
  { key: 'version', title: t('common.version') }
])

const itemColumns = computed<DataTableColumn[]>(() => [
  { key: 'item_code', title: t('dict.item_code') },
  { key: 'item_value', title: t('dict.item_value') },
  { key: 'label_zh_cn', title: t('dict.label_zh_cn') },
  { key: 'label_en_us', title: t('dict.label_en_us') },
  { key: 'sort_order', title: t('common.sort_order') },
  { key: 'status', title: t('common.status') }
])

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadTypes(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listDictTypes({ page_number: 1, page_size: 200 })
    types.value = result.items
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

async function loadItems(typeId: string): Promise<void> {
  itemLoading.value = true
  errorMsg.value = ''
  try {
    items.value = await listDictItems(typeId)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    itemLoading.value = false
  }
}

function selectType(row: DictType): void {
  selectedType.value = row
  loadItems(row.type_code)
}

// ---- Type modal ----

const typeModalVisible = ref(false)
const typeModalMode = ref<'create' | 'edit'>('create')
const typeModel = reactive<Record<string, unknown>>({})

const typeFields = computed<FormField[]>(() => {
  const fields: FormField[] = [
    { key: 'type_code', label: t('dict.type_code'), type: 'text', required: true },
    { key: 'type_name', label: t('dict.type_name'), type: 'text', required: true }
  ]
  if (typeModalMode.value === 'edit') {
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

function openCreateType(): void {
  typeModalMode.value = 'create'
  Object.keys(typeModel).forEach((k) => delete typeModel[k])
  typeModalVisible.value = true
}

function openEditType(row: DictType): void {
  typeModalMode.value = 'edit'
  typeModel.type_code = row.type_code
  typeModel.type_name = row.type_name
  typeModel.status = row.status
  typeModalVisible.value = true
}

async function handleTypeSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (typeModalMode.value === 'create') {
      await createDictType({
        type_code: String(data.type_code),
        type_name: String(data.type_name)
      })
    } else {
      await updateDictType(String(typeModel.type_code), {
        type_name: String(data.type_name ?? ''),
        status: String(data.status ?? 'enabled')
      })
    }
    typeModalVisible.value = false
    successMsg.value = t('common.operation_success')
    await loadTypes()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleDeleteType(row: DictType): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  if (!confirm(t('common.confirm_delete'))) return
  try {
    await deleteDictType(row.type_code)
    if (selectedType.value?.type_code === row.type_code) {
      selectedType.value = null
      items.value = []
    }
    successMsg.value = t('common.operation_success')
    await loadTypes()
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Publish ----

async function handlePublish(row: DictType): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  if (!confirm(t('common.confirm_publish'))) return
  try {
    await publishDictType(row.type_code)
    successMsg.value = t('common.operation_success')
    await loadTypes()
    if (selectedType.value?.type_code === row.type_code) {
      const updated = types.value.find((tp) => tp.type_code === row.type_code)
      if (updated) selectedType.value = updated
    }
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

// ---- Item modal ----

const itemModalVisible = ref(false)
const itemModalMode = ref<'create' | 'edit'>('create')
const itemModel = reactive<Record<string, unknown>>({})

const itemFields = computed<FormField[]>(() => {
  const fields: FormField[] = [
    { key: 'item_code', label: t('dict.item_code'), type: 'text', required: true },
    { key: 'item_value', label: t('dict.item_value'), type: 'text', required: true },
    { key: 'label_zh_cn', label: t('dict.label_zh_cn'), type: 'text', required: true },
    { key: 'label_en_us', label: t('dict.label_en_us'), type: 'text', required: true },
    { key: 'sort_order', label: t('common.sort_order'), type: 'number' }
  ]
  if (itemModalMode.value === 'edit') {
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

function openCreateItem(): void {
  if (!selectedType.value) return
  itemModalMode.value = 'create'
  Object.keys(itemModel).forEach((k) => delete itemModel[k])
  itemModel.type_code = selectedType.value.type_code
  itemModalVisible.value = true
}

function openEditItem(row: DictItem): void {
  itemModalMode.value = 'edit'
  itemModel.item_code = row.item_code
  itemModel.item_value = row.item_value
  itemModel.label_zh_cn = row.label_zh_cn
  itemModel.label_en_us = row.label_en_us
  itemModel.sort_order = row.sort_order
  itemModel.status = row.status
  itemModalVisible.value = true
}

async function handleItemSubmit(data: Record<string, unknown>): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (itemModalMode.value === 'create') {
      await createDictItem(String(itemModel.type_code), {
        item_code: String(data.item_code),
        item_value: String(data.item_value),
        label_zh_cn: String(data.label_zh_cn),
        label_en_us: String(data.label_en_us),
        sort_order: Number(data.sort_order ?? 0)
      })
    } else {
      await updateDictItem(
        String(selectedType.value?.type_code),
        String(itemModel.item_code),
        {
          item_value: String(data.item_value ?? ''),
          label_zh_cn: String(data.label_zh_cn ?? ''),
          label_en_us: String(data.label_en_us ?? ''),
          sort_order: Number(data.sort_order ?? 0),
          status: String(data.status ?? 'enabled')
        }
      )
    }
    itemModalVisible.value = false
    successMsg.value = t('common.operation_success')
    if (selectedType.value) await loadItems(selectedType.value.type_code)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

async function handleDeleteItem(row: DictItem): Promise<void> {
  errorMsg.value = ''
  successMsg.value = ''
  if (!confirm(t('common.confirm_delete'))) return
  try {
    await deleteDictItem(String(selectedType.value?.type_code), row.item_code)
    successMsg.value = t('common.operation_success')
    if (selectedType.value) await loadItems(selectedType.value.type_code)
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  }
}

onMounted(() => {
  loadTypes()
})
</script>

<template>
  <div class="dict-management">
    <h1 class="page-title">{{ t('dict.title') }}</h1>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

    <div class="dict-layout">
      <!-- Left: dict types -->
      <div class="dict-panel">
        <div class="panel-header">
          <span class="panel-title">{{ t('dict.type_list') }}</span>
          <button v-permission="'platform.dictionary.manage'" class="btn btn-primary btn-sm" @click="openCreateType">
            {{ t('common.create') }}
          </button>
        </div>
        <table class="mini-table">
          <thead>
            <tr>
              <th>{{ t('dict.type_code') }}</th>
              <th>{{ t('dict.type_name') }}</th>
              <th>{{ t('common.status') }}</th>
              <th>{{ t('common.version') }}</th>
              <th>{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="5" class="empty-cell">{{ t('common.loading') }}</td>
            </tr>
            <tr v-else-if="types.length === 0">
              <td colspan="5" class="empty-cell">{{ t('common.no_data') }}</td>
            </tr>
            <tr
              v-for="row in types"
              :key="row.type_code"
              :class="{ 'row-selected': selectedType?.type_code === row.type_code }"
              @click="selectType(row)"
            >
              <td class="code-cell">{{ row.type_code }}</td>
              <td>{{ row.type_name }}</td>
              <td><StatusTag :status="row.status" type="enable-disable" /></td>
              <td>v{{ row.version }}</td>
              <td class="actions-cell" @click.stop>
                <button v-permission="'platform.dictionary.manage'" class="btn-link" @click="openEditType(row)">
                  {{ t('common.edit') }}
                </button>
                <button v-permission="'platform.dictionary.manage'" class="btn-link" @click="handlePublish(row)">
                  {{ t('common.publish') }}
                </button>
                <button v-permission="'platform.dictionary.manage'" class="btn-link btn-danger" @click="handleDeleteType(row)">
                  {{ t('common.delete') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Right: dict items -->
      <div class="dict-panel">
        <div class="panel-header">
          <span class="panel-title">
            {{ t('dict.item_list') }}
            <span v-if="selectedType" class="panel-sub">— {{ selectedType.type_name }}</span>
          </span>
          <button
            v-if="selectedType"
            v-permission="'platform.dictionary.manage'"
            class="btn btn-primary btn-sm"
            @click="openCreateItem"
          >
            {{ t('common.create') }}
          </button>
        </div>
        <table class="mini-table">
          <thead>
            <tr>
              <th>{{ t('dict.item_code') }}</th>
              <th>{{ t('dict.item_value') }}</th>
              <th>{{ t('dict.label_zh_cn') }}</th>
              <th>{{ t('dict.label_en_us') }}</th>
              <th>{{ t('common.sort_order') }}</th>
              <th>{{ t('common.status') }}</th>
              <th>{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!selectedType">
              <td colspan="7" class="empty-cell">{{ t('common.no_data') }}</td>
            </tr>
            <tr v-else-if="itemLoading">
              <td colspan="7" class="empty-cell">{{ t('common.loading') }}</td>
            </tr>
            <tr v-else-if="items.length === 0">
              <td colspan="7" class="empty-cell">{{ t('common.no_data') }}</td>
            </tr>
            <tr v-for="row in items" :key="row.item_code">
              <td class="code-cell">{{ row.item_code }}</td>
              <td>{{ row.item_value }}</td>
              <td>{{ row.label_zh_cn }}</td>
              <td>{{ row.label_en_us }}</td>
              <td>{{ row.sort_order }}</td>
              <td><StatusTag :status="row.status" type="enable-disable" /></td>
              <td class="actions-cell">
                <button v-permission="'platform.dictionary.manage'" class="btn-link" @click="openEditItem(row)">
                  {{ t('common.edit') }}
                </button>
                <button v-permission="'platform.dictionary.manage'" class="btn-link btn-danger" @click="handleDeleteItem(row)">
                  {{ t('common.delete') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Type create/edit -->
    <ModalDialog
      :visible="typeModalVisible"
      :title="typeModalMode === 'create' ? t('dict.create_type_title') : t('dict.edit_type_title')"
      :width="440"
      @close="typeModalVisible = false"
    >
      <FormBuilder
        :fields="typeFields"
        :model-value="typeModel"
        @submit="handleTypeSubmit"
        @cancel="typeModalVisible = false"
      />
    </ModalDialog>

    <!-- Item create/edit -->
    <ModalDialog
      :visible="itemModalVisible"
      :title="itemModalMode === 'create' ? t('dict.create_item_title') : t('dict.edit_item_title')"
      :width="480"
      @close="itemModalVisible = false"
    >
      <FormBuilder
        :fields="itemFields"
        :model-value="itemModel"
        @submit="handleItemSubmit"
        @cancel="itemModalVisible = false"
      />
    </ModalDialog>
  </div>
</template>

<style scoped>
.dict-management {
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

.dict-layout {
  display: flex;
  gap: 1rem;
}

.dict-panel {
  flex: 1;
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  overflow: auto;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e2e8f0;
  background-color: #f8fafc;
}

.panel-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1e293b;
}

.panel-sub {
  font-weight: 400;
  color: #64748b;
}

.mini-table {
  width: 100%;
  border-collapse: collapse;
}

.mini-table thead th {
  padding: 0.5rem 0.625rem;
  text-align: left;
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}

.mini-table tbody td {
  padding: 0.5rem 0.625rem;
  font-size: 0.8125rem;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
}

.mini-table tbody tr {
  cursor: pointer;
}

.mini-table tbody tr:hover {
  background-color: #f8fafc;
}

.row-selected {
  background-color: #dbeafe !important;
}

.code-cell {
  font-family: monospace;
  font-size: 0.75rem;
  color: #2563eb;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  padding: 1.5rem;
  cursor: default;
}

.actions-cell {
  white-space: nowrap;
}

.btn {
  border: none;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-sm {
  padding: 0.375rem 0.875rem;
}

.btn-primary {
  background-color: #2563eb;
  color: #ffffff;
}

.btn-primary:hover {
  background-color: #1d4ed8;
}

.btn-link {
  background: transparent;
  border: none;
  color: #2563eb;
  font-size: 0.75rem;
  cursor: pointer;
  padding: 0;
  margin-right: 0.5rem;
}

.btn-link:hover {
  text-decoration: underline;
}

.btn-link.btn-danger {
  color: #dc2626;
}
</style>
