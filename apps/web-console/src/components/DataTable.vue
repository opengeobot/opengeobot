<script setup lang="ts">
// Function: Reusable data table with sorting, pagination and loading overlay
// Time: 2026-07-04
// Author: AxeXie
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DataTableColumn, DataTablePagination, SortState, SortDirection } from '@/types/api'

const props = withDefaults(defineProps<{
  columns: DataTableColumn[]
  data: Record<string, unknown>[]
  loading?: boolean
  pagination?: DataTablePagination | null
}>(), {
  loading: false,
  pagination: null
})

const emit = defineEmits<{
  'sort-change': [sort: SortState]
  'page-change': [page: number]
  'size-change': [size: number]
}>()

const { t } = useI18n()

const sortKey = ref<string | null>(null)
const sortDirection = ref<SortDirection>('asc')

const pageSizeOptions = [10, 20, 50]

const totalPages = computed<number>(() => {
  if (!props.pagination) return 0
  return Math.max(1, Math.ceil(props.pagination.total / props.pagination.page_size))
})

function handleSort(column: DataTableColumn): void {
  if (!column.sortable) return
  if (sortKey.value === column.key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = column.key
    sortDirection.value = 'asc'
  }
  emit('sort-change', { key: sortKey.value, direction: sortDirection.value })
}

function handlePrevPage(): void {
  if (!props.pagination) return
  if (props.pagination.page_number > 1) {
    emit('page-change', props.pagination.page_number - 1)
  }
}

function handleNextPage(): void {
  if (!props.pagination) return
  if (props.pagination.page_number < totalPages.value) {
    emit('page-change', props.pagination.page_number + 1)
  }
}

function handleSizeChange(event: Event): void {
  const size = Number((event.target as HTMLSelectElement).value)
  emit('size-change', size)
}

function getSortClass(key: string): string {
  if (sortKey.value !== key) return ''
  return sortDirection.value === 'asc' ? 'sort-asc' : 'sort-desc'
}

function columnStyle(column: DataTableColumn): Record<string, string> {
  if (!column.width) return {}
  const width = typeof column.width === 'number' ? `${column.width}px` : column.width
  return { width }
}

function formatValue(row: Record<string, unknown>, key: string): string {
  const value = row[key]
  if (value === null || value === undefined) return ''
  if (typeof value === 'boolean') return value ? '✓' : '✗'
  return String(value)
}
</script>

<template>
  <div class="data-table-wrapper">
    <div v-if="loading" class="loading-overlay">
      <span class="loading-text">{{ t('common.loading') }}</span>
    </div>

    <table class="data-table">
      <thead>
        <tr>
          <th
            v-for="column in columns"
            :key="column.key"
            :style="columnStyle(column)"
            :class="{ sortable: column.sortable, [getSortClass(column.key)]: true }"
            @click="handleSort(column)"
          >
            <slot :name="`header-${column.key}`" :column="column">
              {{ column.title }}
              <span v-if="column.sortable && sortKey === column.key" class="sort-indicator">
                {{ sortDirection === 'asc' ? '↑' : '↓' }}
              </span>
            </slot>
          </th>
          <th v-if="$slots.actions" class="actions-header">{{ t('common.actions') }}</th>
        </tr>
      </thead>

      <tbody>
        <tr v-if="data.length === 0 && !loading">
          <td :colspan="columns.length + ($slots.actions ? 1 : 0)" class="empty-cell">
            {{ t('common.no_data') }}
          </td>
        </tr>

        <tr v-for="(row, index) in data" :key="index">
          <td v-for="column in columns" :key="column.key">
            <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">
              {{ formatValue(row, column.key) }}
            </slot>
          </td>
          <td v-if="$slots.actions" class="actions-cell">
            <slot name="actions" :row="row" :index="index" />
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="pagination" class="pagination-bar">
      <span class="pagination-total">
        {{ t('common.total') }}: {{ pagination.total }}
      </span>

      <div class="pagination-controls">
        <select
          class="size-select"
          :value="pagination.page_size"
          @change="handleSizeChange"
        >
          <option v-for="size in pageSizeOptions" :key="size" :value="size">
            {{ size }} / {{ t('common.page') }}
          </option>
        </select>

        <button
          class="page-btn"
          :disabled="pagination.page_number <= 1"
          @click="handlePrevPage"
        >
          ‹
        </button>

        <span class="page-indicator">
          {{ pagination.page_number }} / {{ totalPages }}
        </span>

        <button
          class="page-btn"
          :disabled="pagination.page_number >= totalPages"
          @click="handleNextPage"
        >
          ›
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.data-table-wrapper {
  position: relative;
  overflow-x: auto;
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(255, 255, 255, 0.75);
  z-index: 5;
}

.loading-text {
  font-size: 0.875rem;
  color: #64748b;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table thead th {
  padding: 0.625rem 0.75rem;
  text-align: left;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #475569;
  background-color: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}

.sortable {
  cursor: pointer;
  user-select: none;
}

.sortable:hover {
  background-color: #f1f5f9;
}

.sort-indicator {
  margin-left: 0.25rem;
  font-size: 0.75rem;
}

.data-table tbody td {
  padding: 0.625rem 0.75rem;
  font-size: 0.8125rem;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr:hover {
  background-color: #f8fafc;
}

.empty-cell {
  text-align: center;
  color: #94a3b8;
  padding: 2rem;
  font-size: 0.875rem;
}

.actions-header {
  text-align: right;
}

.actions-cell {
  text-align: right;
  white-space: nowrap;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 0.75rem;
  border-top: 1px solid #e2e8f0;
}

.pagination-total {
  font-size: 0.8125rem;
  color: #64748b;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.size-select {
  padding: 0.25rem 0.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.25rem;
  font-size: 0.8125rem;
  cursor: pointer;
}

.page-btn {
  padding: 0.25rem 0.625rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.25rem;
  background-color: #ffffff;
  font-size: 0.875rem;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  background-color: #f1f5f9;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-indicator {
  font-size: 0.8125rem;
  color: #475569;
  min-width: 3rem;
  text-align: center;
}
</style>
