<script setup lang="ts">
// Function: Read-only permission view grouped by module with role filter
// Time: 2026-07-04
// Author: AxeXie
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listPermissionsByModule, getPermissionsByRole } from '@/api/permission'
import { listRoles } from '@/api/role'
import type { PermissionGroup, Role, Permission, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()

const permGroups = ref<PermissionGroup[]>([])
const roles = ref<Role[]>([])
const selectedRoleId = ref<string>('')
const assignedCodes = ref<Set<string>>(new Set())
const loading = ref(false)
const errorMsg = ref('')

function resolveError(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

async function loadRoles(): Promise<void> {
  try {
    const result = await listRoles({ page_number: 1, page_size: 200 })
    roles.value = result.items
  } catch {
    // Roles optional for filter
  }
}

async function loadPermissions(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  assignedCodes.value = new Set()
  try {
    permGroups.value = await listPermissionsByModule()
    if (selectedRoleId.value) {
      const assigned = await getPermissionsByRole(selectedRoleId.value)
      assignedCodes.value = new Set(assigned.map((p: Permission) => p.permission_code))
    }
  } catch (err) {
    errorMsg.value = resolveError(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

function handleRoleChange(): void {
  loadPermissions()
}

const totalPermissions = computed<number>(() =>
  permGroups.value.reduce((sum, g) => sum + g.permissions.length, 0)
)

onMounted(() => {
  loadRoles()
  loadPermissions()
})
</script>

<template>
  <div class="permission-view">
    <h1 class="page-title">{{ t('permission.title') }}</h1>

    <div class="toolbar">
      <label class="filter-label">{{ t('permission.filter_by_role') }}:</label>
      <select v-model="selectedRoleId" class="filter-select" @change="handleRoleChange">
        <option value="">{{ t('common.all') }}</option>
        <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.role_name }}</option>
      </select>
      <span class="total-count">{{ t('common.total') }}: {{ totalPermissions }}</span>
    </div>

    <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
    <p v-if="loading" class="loading-text">{{ t('common.loading') }}</p>

    <div v-else class="perm-module-list">
      <div v-for="group in permGroups" :key="group.module" class="perm-module">
        <h2 class="module-header">
          <span class="module-name">{{ group.module }}</span>
          <span class="module-count">({{ group.permissions.length }})</span>
        </h2>
        <table class="perm-table">
          <thead>
            <tr>
              <th>{{ t('permission.permission_code') }}</th>
              <th>{{ t('permission.permission_name') }}</th>
              <th class="col-desc">{{ t('common.description') }}</th>
              <th v-if="selectedRoleId">{{ t('permission.assigned') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="perm in group.permissions" :key="perm.permission_code">
              <td class="code-cell">{{ perm.permission_code }}</td>
              <td>{{ perm.permission_name }}</td>
              <td class="desc-cell">{{ perm.description || '—' }}</td>
              <td v-if="selectedRoleId" class="check-cell">
                <span v-if="assignedCodes.has(perm.permission_code)" class="check-yes">✓</span>
                <span v-else class="check-no">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.permission-view {
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
  align-items: center;
  gap: 0.75rem;
}

.filter-label {
  font-size: 0.875rem;
  color: #475569;
  font-weight: 500;
}

.filter-select {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  min-width: 14rem;
}

.total-count {
  font-size: 0.8125rem;
  color: #64748b;
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

.loading-text {
  color: #64748b;
  font-size: 0.875rem;
}

.perm-module-list {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.perm-module {
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  overflow: hidden;
}

.module-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background-color: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #1e293b;
}

.module-name {
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

.module-count {
  font-size: 0.75rem;
  color: #94a3b8;
  font-weight: 400;
}

.perm-table {
  width: 100%;
  border-collapse: collapse;
}

.perm-table thead th {
  padding: 0.5rem 1rem;
  text-align: left;
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
  background-color: #fafbfc;
  border-bottom: 1px solid #f1f5f9;
}

.perm-table tbody td {
  padding: 0.5rem 1rem;
  font-size: 0.8125rem;
  color: #1e293b;
  border-bottom: 1px solid #f8fafc;
}

.code-cell {
  font-family: monospace;
  font-size: 0.75rem;
  color: #2563eb;
}

.desc-cell {
  color: #64748b;
}

.col-desc {
  min-width: 12rem;
}

.check-cell {
  text-align: center;
}

.check-yes {
  color: #16a34a;
  font-weight: 600;
}

.check-no {
  color: #cbd5e1;
}
</style>
