<template>
  <div class="space-y-6">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-blue-600">{{ stats.total_tasks || 0 }}</div>
            <div class="text-gray-500 mt-2">总任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-orange-600">{{ stats.pending_tasks || 0 }}</div>
            <div class="text-gray-500 mt-2">待处理</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-green-600">{{ stats.completed_tasks || 0 }}</div>
            <div class="text-gray-500 mt-2">已完成</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="text-center">
            <div class="text-3xl font-bold text-purple-600">{{ stats.acceptance_rate ? (stats.acceptance_rate * 100).toFixed(1) + '%' : '0%' }}</div>
            <div class="text-gray-500 mt-2">接受率</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选与操作 -->
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">引用建设任务</h3>
          <el-button type="primary" @click="showCreateDialog = true">新建任务</el-button>
        </div>
      </template>

      <div class="mb-4 flex gap-4">
        <el-select v-model="filterStatus" placeholder="状态筛选" clearable @change="loadTasks" style="width: 150px">
          <el-option label="待处理" value="pending" />
          <el-option label="进行中" value="in_progress" />
          <el-option label="已完成" value="completed" />
          <el-option label="失败" value="failed" />
        </el-select>
        <el-select v-model="filterPriority" placeholder="优先级筛选" clearable @change="loadTasks" style="width: 150px">
          <el-option label="低" value="low" />
          <el-option label="中" value="medium" />
          <el-option label="高" value="high" />
          <el-option label="紧急" value="critical" />
        </el-select>
        <el-select v-model="filterType" placeholder="类型筛选" clearable @change="loadTasks" style="width: 150px">
          <el-option label="反向链接" value="backlink" />
          <el-option label="引用" value="citation" />
          <el-option label="提及" value="mention" />
          <el-option label="集成" value="integration" />
        </el-select>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="task_id" label="ID" width="100" />
        <el-table-column label="目标" min-width="200">
          <template #default="{ row }">
            <div>
              <div class="font-medium">{{ row.target?.title || row.target?.domain }}</div>
              <div class="text-xs text-gray-500 truncate">{{ row.target?.url }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="reference_type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.reference_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80">
          <template #default="{ row }">
            <el-tag :type="getPriorityType(row.priority)" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="outreach_count" label="外联次数" width="90" />
        <el-table-column prop="target.quality_score" label="质量分" width="80" />
        <el-table-column label="截止" width="120">
          <template #default="{ row }">
            {{ row.deadline ? new Date(row.deadline).toLocaleDateString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" @click="handleStatusChange(row)">更新状态</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建引用建设任务" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="目标 URL">
          <el-input v-model="form.url" placeholder="https://example.com" />
        </el-form-item>
        <el-form-item label="域名">
          <el-input v-model="form.domain" placeholder="example.com" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="网站/页面标题" />
        </el-form-item>
        <el-form-item label="引用类型">
          <el-select v-model="form.reference_type" style="width: 100%">
            <el-option label="反向链接" value="backlink" />
            <el-option label="引用" value="citation" />
            <el-option label="提及" value="mention" />
            <el-option label="集成" value="integration" />
            <el-option label="合作" value="partnership" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option label="低" value="low" />
            <el-option label="中" value="medium" />
            <el-option label="高" value="high" />
            <el-option label="紧急" value="critical" />
          </el-select>
        </el-form-item>
        <el-form-item label="质量分">
          <el-input-number v-model="form.quality_score" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item label="最大外联次数">
          <el-input-number v-model="form.max_outreach_attempts" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.notes" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">创建</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="任务详情" width="800px">
      <div v-if="selectedTask" class="space-y-4">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务 ID">{{ selectedTask.task_id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(selectedTask.status)">{{ selectedTask.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标 URL" :span="2">{{ selectedTask.target?.url }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ selectedTask.reference_type }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ selectedTask.priority }}</el-descriptions-item>
          <el-descriptions-item label="外联次数">{{ selectedTask.outreach_count }}</el-descriptions-item>
          <el-descriptions-item label="质量分">{{ selectedTask.target?.quality_score }}</el-descriptions-item>
        </el-descriptions>

        <!-- 外联记录 -->
        <h4 class="font-semibold mt-4">外联记录</h4>
        <el-table :data="outreachRecords" size="small">
          <el-table-column prop="channel" label="渠道" width="120" />
          <el-table-column prop="contact_email" label="联系邮箱" width="180" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="created_at" label="创建时间" width="160">
            <template #default="{ row }">
              {{ formatDate(row.created_at) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getReferenceTasks,
  createReferenceTask,
  updateReferenceTask,
  deleteReferenceTask,
  getReferenceStatistics,
  getOutreachRecords,
} from '@/api'

const projectStore = useProjectStore()
const tasks = ref([])
const stats = ref({})
const loading = ref(false)
const submitting = ref(false)
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const selectedTask = ref(null)
const outreachRecords = ref([])

const filterStatus = ref('')
const filterPriority = ref('')
const filterType = ref('')

const form = ref({
  url: '',
  domain: '',
  title: '',
  reference_type: 'backlink',
  priority: 'medium',
  quality_score: 0.5,
  max_outreach_attempts: 5,
  notes: '',
})

const getStatusType = (status) => {
  const map = { pending: 'info', in_progress: '', completed: 'success', failed: 'danger', cancelled: 'warning' }
  return map[status] || 'info'
}

const getPriorityType = (priority) => {
  const map = { low: 'info', medium: '', high: 'warning', critical: 'danger' }
  return map[priority] || 'info'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const loadTasks = async () => {
  loading.value = true
  try {
    const projectId = projectStore.currentProjectId
    if (!projectId) return
    const params = {}
    if (filterStatus.value) params.status = filterStatus.value
    if (filterPriority.value) params.priority = filterPriority.value
    if (filterType.value) params.reference_type = filterType.value
    const res = await getReferenceTasks(projectId, params)
    tasks.value = res.tasks || []
  } catch (e) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const projectId = projectStore.currentProjectId
    if (!projectId) return
    stats.value = await getReferenceStatistics(projectId)
  } catch (e) {
    // Error handled by interceptor
  }
}

const handleSubmit = async () => {
  if (!form.value.url || !form.value.domain) {
    ElMessage.warning('请填写 URL 和域名')
    return
  }
  submitting.value = true
  try {
    const projectId = projectStore.currentProjectId
    await createReferenceTask(projectId, form.value)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    form.value = { url: '', domain: '', title: '', reference_type: 'backlink', priority: 'medium', quality_score: 0.5, max_outreach_attempts: 5, notes: '' }
    await loadTasks()
    await loadStats()
  } catch (e) {
    // Error handled by interceptor
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除此任务吗？`, '确认删除', { type: 'warning' })
    const projectId = projectStore.currentProjectId
    await deleteReferenceTask(projectId, row.task_id)
    ElMessage.success('删除成功')
    await loadTasks()
    await loadStats()
  } catch (e) {
    if (e !== 'cancel') {}
  }
}

const handleStatusChange = async (row) => {
  const { value } = await ElMessageBox.prompt('输入新状态 (pending/in_progress/completed/failed)', '更新状态', {
    inputValue: row.status,
  })
  try {
    const projectId = projectStore.currentProjectId
    await updateReferenceTask(projectId, row.task_id, { status: value })
    ElMessage.success('状态已更新')
    await loadTasks()
  } catch (e) {
    // Error handled by interceptor
  }
}

const viewDetail = async (row) => {
  selectedTask.value = row
  showDetailDialog.value = true
  try {
    const projectId = projectStore.currentProjectId
    const res = await getOutreachRecords(projectId, row.task_id)
    outreachRecords.value = res.records || []
  } catch (e) {
    // Error handled by interceptor
  }
}

onMounted(() => {
  loadTasks()
  loadStats()
})
</script>
