<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">运行列表</h3>
          <el-button type="primary" @click="showCreateDialog = true">创建运行</el-button>
        </div>
      </template>
      
      <el-table :data="runs" v-loading="loading">
        <el-table-column prop="run_id" label="Run ID" width="200" />
        <el-table-column prop="run_type" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="prompt_count" label="提示词数" width="100" />
        <el-table-column prop="started_at" label="开始时间" width="180" />
        <el-table-column label="指标" width="150">
          <template #default="{ row }">
            <span class="text-xs">MR: {{ row.metrics?.mention_rate?.toFixed(2) || 0 }}</span>
            <span class="text-xs ml-2">CR: {{ row.metrics?.citation_rate?.toFixed(2) || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="viewRun(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showCreateDialog" title="创建运行" width="500px">
      <el-form :model="newRun" label-width="100px">
        <el-form-item label="运行类型">
          <el-select v-model="newRun.run_type" style="width: 100%">
            <el-option label="基线运行" value="baseline" />
            <el-option label="验证运行" value="after" />
            <el-option label="按需运行" value="on-demand" />
          </el-select>
        </el-form-item>
        <el-form-item label="引擎">
          <el-select v-model="newRun.engines" multiple style="width: 100%">
            <el-option label="引擎 Alpha" value="engine_alpha" />
            <el-option label="引擎 Beta" value="engine_beta" />
          </el-select>
        </el-form-item>
        <el-form-item label="异步模式">
          <el-switch v-model="newRun.async_mode" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createRun">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRunDetail" title="运行详情" width="800px">
      <el-descriptions v-if="selectedRun" :column="2" border>
        <el-descriptions-item label="Run ID">{{ selectedRun.run_id }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ selectedRun.run_type }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedRun.status }}</el-descriptions-item>
        <el-descriptions-item label="提示词数">{{ selectedRun.prompt_count }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ selectedRun.started_at }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ selectedRun.finished_at }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="selectedRun?.metrics" class="mt-4">
        <h4 class="font-bold mb-2">指标</h4>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="提及率">{{ selectedRun.metrics.mention_rate }}</el-descriptions-item>
          <el-descriptions-item label="引用率">{{ selectedRun.metrics.citation_rate }}</el-descriptions-item>
          <el-descriptions-item label="平均位置">{{ selectedRun.metrics.average_position }}</el-descriptions-item>
          <el-descriptions-item label="情感分">{{ selectedRun.metrics.sentiment_score }}</el-descriptions-item>
          <el-descriptions-item label="声量份额">{{ selectedRun.metrics.share_of_voice }}</el-descriptions-item>
          <el-descriptions-item label="引用质量">{{ selectedRun.metrics.citation_quality }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getRuns, createRun as createRunApi, getRun as getRunApi } from '@/api'
import { ElMessage } from 'element-plus'

const projectStore = useProjectStore()
const runs = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)
const showRunDetail = ref(false)
const selectedRun = ref(null)

const newRun = ref({
  run_type: 'on-demand',
  engines: ['engine_alpha', 'engine_beta'],
  async_mode: false,
})

const getStatusType = (status) => {
  const map = { success: 'success', failed: 'danger', running: 'warning', pending: 'info', partial_success: 'warning' }
  return map[status] || 'info'
}

const loadRuns = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    runs.value = await getRuns(projectStore.currentProjectId)
  } finally {
    loading.value = false
  }
}

const createRun = async () => {
  try {
    const { async_mode, ...data } = newRun.value
    await createRunApi(projectStore.currentProjectId, data, async_mode)
    showCreateDialog.value = false
    await loadRuns()
    ElMessage.success('运行创建成功')
  } catch (error) {
    console.error(error)
  }
}

const viewRun = async (run) => {
  try {
    selectedRun.value = await getRunApi(projectStore.currentProjectId, run.run_id)
    showRunDetail.value = true
  } catch (error) {
    console.error(error)
  }
}

onMounted(loadRuns)
</script>
