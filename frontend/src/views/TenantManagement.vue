<template>
  <div class="space-y-6">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-blue-600">{{ tenants.length }}</div>
            <div class="text-gray-500 mt-2">租户总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-green-600">{{ freeTenants }}</div>
            <div class="text-gray-500 mt-2">免费层</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-orange-600">{{ proTenants }}</div>
            <div class="text-gray-500 mt-2">专业层</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-purple-600">{{ enterpriseTenants }}</div>
            <div class="text-gray-500 mt-2">企业层</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 租户列表 -->
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">租户管理</h3>
          <el-button type="primary" @click="showCreateDialog = true">创建租户</el-button>
        </div>
      </template>

      <el-table :data="tenants" v-loading="loading" stripe>
        <el-table-column prop="tenant_id" label="ID" width="120" />
        <el-table-column prop="tenant_name" label="租户名称" min-width="150" />
        <el-table-column prop="tier" label="层级" width="120">
          <template #default="{ row }">
            <el-tag :type="getTierType(row.tier)">{{ row.tier }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="max_projects" label="项目上限" width="100" />
        <el-table-column prop="max_users" label="用户上限" width="100" />
        <el-table-column prop="created_at" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.created_at) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审计日志 -->
    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">审计日志</h3>
      </template>

      <el-table :data="auditLogs" v-loading="auditLoading" stripe>
        <el-table-column prop="log_id" label="ID" width="120" />
        <el-table-column prop="tenant_id" label="租户 ID" width="120" />
        <el-table-column prop="user_id" label="用户 ID" width="120" />
        <el-table-column prop="action" label="操作" width="100">
          <template #default="{ row }">
            <el-tag :type="getActionType(row.action)">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resource" label="资源" width="100" />
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="details" label="详情" min-width="200">
          <template #default="{ row }">
            <pre class="text-xs">{{ JSON.stringify(row.details) }}</pre>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑租户对话框 -->
    <el-dialog v-model="showCreateDialog" :title="editingTenant ? '编辑租户' : '创建租户'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="租户名称">
          <el-input v-model="form.tenant_name" placeholder="输入租户名称" />
        </el-form-item>
        <el-form-item label="层级">
          <el-select v-model="form.tier" placeholder="选择层级">
            <el-option label="免费 (Free)" value="free" />
            <el-option label="专业 (Pro)" value="pro" />
            <el-option label="企业 (Enterprise)" value="enterprise" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ editingTenant ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTenants,
  createTenant,
  updateTenant,
  deleteTenant,
  getAuditLogs,
} from '@/api'

const tenants = ref([])
const auditLogs = ref([])
const loading = ref(false)
const auditLoading = ref(false)
const showCreateDialog = ref(false)
const editingTenant = ref(null)
const submitting = ref(false)

const form = ref({
  tenant_name: '',
  tier: 'free',
})

const freeTenants = computed(() => tenants.value.filter(t => t.tier === 'free').length)
const proTenants = computed(() => tenants.value.filter(t => t.tier === 'pro').length)
const enterpriseTenants = computed(() => tenants.value.filter(t => t.tier === 'enterprise').length)

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getTierType = (tier) => {
  const map = { free: 'info', pro: '', enterprise: 'warning' }
  return map[tier] || 'info'
}

const getActionType = (action) => {
  const map = { create: 'success', read: 'info', update: '', delete: 'danger' }
  return map[action] || 'info'
}

const loadTenants = async () => {
  loading.value = true
  try {
    const res = await getTenants()
    tenants.value = res.items || []
  } catch (e) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}

const loadAuditLogs = async () => {
  auditLoading.value = true
  try {
    const res = await getAuditLogs({ page_size: 50 })
    auditLogs.value = res.items || []
  } catch (e) {
    // Error handled by interceptor
  } finally {
    auditLoading.value = false
  }
}

const handleEdit = (row) => {
  editingTenant.value = row
  form.value = {
    tenant_name: row.tenant_name,
    tier: row.tier,
  }
  showCreateDialog.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除租户 "${row.tenant_name}" 吗？`, '确认删除', {
      type: 'warning',
    })
    await deleteTenant(row.tenant_id)
    ElMessage.success('删除成功')
    await loadTenants()
  } catch (e) {
    if (e !== 'cancel') {
      // Error handled by interceptor
    }
  }
}

const handleSubmit = async () => {
  if (!form.value.tenant_name) {
    ElMessage.warning('请输入租户名称')
    return
  }
  submitting.value = true
  try {
    if (editingTenant.value) {
      await updateTenant(editingTenant.value.tenant_id, form.value)
      ElMessage.success('更新成功')
    } else {
      await createTenant(form.value)
      ElMessage.success('创建成功')
    }
    showCreateDialog.value = false
    editingTenant.value = null
    form.value = { tenant_name: '', tier: 'free' }
    await loadTenants()
  } catch (e) {
    // Error handled by interceptor
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadTenants()
  loadAuditLogs()
})
</script>
