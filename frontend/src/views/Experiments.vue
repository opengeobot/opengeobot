<template>
  <div class="space-y-6">
    <!-- 实验设计列表 -->
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">实验设计</h3>
          <div class="flex gap-2">
            <el-button @click="autoGenerateSuggestions" :loading="suggestionLoading">自动生成建议</el-button>
            <el-button type="primary" @click="showCreateDialog = true">新建实验</el-button>
          </div>
        </div>
      </template>

      <el-table :data="experiments" v-loading="loading" stripe>
        <el-table-column prop="experiment_id" label="ID" width="100" />
        <el-table-column prop="name" label="实验名称" min-width="150" />
        <el-table-column prop="experiment_type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.experiment_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hypothesis" label="假设" min-width="200" show-overflow-tooltip />
        <el-table-column prop="duration_days" label="持续天数" width="100" />
        <el-table-column prop="confidence_level" label="置信度" width="100">
          <template #default="{ row }">
            {{ (row.confidence_level * 100).toFixed(0) }}%
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="viewResults(row)">结果</el-button>
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 实验建议 -->
    <el-card v-if="suggestions.length > 0">
      <template #header>
        <h3 class="text-lg font-semibold">自动实验建议</h3>
      </template>
      <el-table :data="suggestions" stripe>
        <el-table-column prop="title" label="建议" min-width="200" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80">
          <template #default="{ row }">
            <el-tag :type="row.priority >= 4 ? 'danger' : row.priority >= 3 ? 'warning' : 'info'" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expected_impact" label="预期影响" width="150" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="createFromSuggestion(row)">采用</el-button>
            <el-button size="small" @click="dismissSuggestion(row)">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑实验对话框 -->
    <el-dialog v-model="showCreateDialog" :title="editingExperiment ? '编辑实验' : '新建实验'" width="700px">
      <el-form :model="form" label-width="140px">
        <el-form-item label="实验名称">
          <el-input v-model="form.name" placeholder="输入实验名称" />
        </el-form-item>
        <el-form-item label="实验类型">
          <el-select v-model="form.experiment_type" style="width: 100%">
            <el-option label="A/B 测试" value="ab_test" />
            <el-option label="多变量测试" value="multivariate" />
            <el-option label="URL 分割" value="split_url" />
            <el-option label="前后对比" value="before_after" />
          </el-select>
        </el-form-item>
        <el-form-item label="假设">
          <el-input v-model="form.hypothesis" type="textarea" :rows="3" placeholder="描述你的实验假设" />
        </el-form-item>
        <el-form-item label="持续天数">
          <el-input-number v-model="form.duration_days" :min="1" :max="90" />
        </el-form-item>
        <el-form-item label="置信度">
          <el-select v-model="form.confidence_level" style="width: 100%">
            <el-option label="90%" :value="0.90" />
            <el-option label="95%" :value="0.95" />
            <el-option label="99%" :value="0.99" />
          </el-select>
        </el-form-item>
        <el-form-item label="最小可检测效应">
          <el-input-number v-model="form.min_detectable_effect" :min="0.01" :max="0.5" :step="0.01" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ editingExperiment ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 实验结果对话框 -->
    <el-dialog v-model="showResultsDialog" title="实验结果" width="800px">
      <div v-if="selectedResults" class="space-y-4">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">{{ selectedResults.status }}</el-descriptions-item>
          <el-descriptions-item label="统计功效">{{ (selectedResults.statistical_power * 100).toFixed(1) }}%</el-descriptions-item>
          <el-descriptions-item label="P 值">{{ selectedResults.p_value }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ (selectedResults.confidence_level * 100).toFixed(0) }}%</el-descriptions-item>
        </el-descriptions>

        <h4 class="font-semibold mt-4">变体结果</h4>
        <el-table :data="selectedResults.variants" size="small">
          <el-table-column prop="variant_name" label="变体" width="120" />
          <el-table-column prop="sample_size" label="样本量" width="100" />
          <el-table-column prop="conversion_rate" label="转化率" width="100">
            <template #default="{ row }">
              {{ (row.conversion_rate * 100).toFixed(2) }}%
            </template>
          </el-table-column>
          <el-table-column prop="is_winner" label="获胜" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.is_winner" type="success" size="small">获胜</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="statistical_significance" label="显著性" width="100">
            <template #default="{ row }">
              {{ (row.statistical_significance * 100).toFixed(1) }}%
            </template>
          </el-table-column>
        </el-table>

        <h4 class="font-semibold mt-4">建议</h4>
        <ul class="list-disc pl-5 space-y-1">
          <li v-for="(rec, idx) in selectedResults.recommendations" :key="idx" class="text-sm">{{ rec }}</li>
        </ul>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getExperiments,
  createExperiment,
  updateExperiment,
  deleteExperiment,
  getExperimentResults,
  getExperimentSuggestions,
  dismissExperimentSuggestion,
  autoGenerateSuggestions as autoGenerateSuggestionsAPI,
} from '@/api'

const projectStore = useProjectStore()
const experiments = ref([])
const suggestions = ref([])
const loading = ref(false)
const submitting = ref(false)
const suggestionLoading = ref(false)
const showCreateDialog = ref(false)
const showResultsDialog = ref(false)
const editingExperiment = ref(null)
const selectedResults = ref(null)

const form = ref({
  name: '',
  experiment_type: 'ab_test',
  hypothesis: '',
  duration_days: 14,
  confidence_level: 0.95,
  min_detectable_effect: 0.05,
  variants: [],
  goals: [],
})

const getStatusType = (status) => {
  const map = { draft: 'info', running: '', completed: 'success', paused: 'warning', cancelled: 'danger' }
  return map[status] || 'info'
}

const loadExperiments = async () => {
  loading.value = true
  try {
    const projectId = projectStore.currentProjectId
    if (!projectId) return
    const res = await getExperiments(projectId)
    experiments.value = res.experiments || []
  } catch (e) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}

const loadSuggestions = async () => {
  try {
    const projectId = projectStore.currentProjectId
    if (!projectId) return
    const res = await getExperimentSuggestions(projectId)
    suggestions.value = res.suggestions || []
  } catch (e) {
    // Error handled by interceptor
  }
}

const handleSubmit = async () => {
  if (!form.value.name) {
    ElMessage.warning('请输入实验名称')
    return
  }
  submitting.value = true
  try {
    const projectId = projectStore.currentProjectId
    if (editingExperiment.value) {
      await updateExperiment(projectId, editingExperiment.value.experiment_id, form.value)
      ElMessage.success('更新成功')
    } else {
      await createExperiment(projectId, form.value)
      ElMessage.success('创建成功')
    }
    showCreateDialog.value = false
    editingExperiment.value = null
    form.value = { name: '', experiment_type: 'ab_test', hypothesis: '', duration_days: 14, confidence_level: 0.95, min_detectable_effect: 0.05, variants: [], goals: [] }
    await loadExperiments()
  } catch (e) {
    // Error handled by interceptor
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除实验 "${row.name}" 吗？`, '确认删除', { type: 'warning' })
    const projectId = projectStore.currentProjectId
    await deleteExperiment(projectId, row.experiment_id)
    ElMessage.success('删除成功')
    await loadExperiments()
  } catch (e) {
    if (e !== 'cancel') {}
  }
}

const handleEdit = (row) => {
  editingExperiment.value = row
  form.value = {
    name: row.name,
    experiment_type: row.experiment_type,
    hypothesis: row.hypothesis,
    duration_days: row.duration_days,
    confidence_level: row.confidence_level,
    min_detectable_effect: row.min_detectable_effect,
    variants: row.variants || [],
    goals: row.goals || [],
  }
  showCreateDialog.value = true
}

const viewResults = async (row) => {
  try {
    const projectId = projectStore.currentProjectId
    const res = await getExperimentResults(projectId, row.experiment_id)
    if (res.results && res.results.length > 0) {
      selectedResults.value = res.results[0]
    } else {
      ElMessage.info('暂无实验结果')
      return
    }
    showResultsDialog.value = true
  } catch (e) {
    // Error handled by interceptor
  }
}

const autoGenerateSuggestions = async () => {
  suggestionLoading.value = true
  try {
    const projectId = projectStore.currentProjectId
    await autoGenerateSuggestionsAPI(projectId)
    ElMessage.success('建议已生成')
    await loadSuggestions()
  } catch (e) {
    // Error handled by interceptor
  } finally {
    suggestionLoading.value = false
  }
}

const dismissSuggestion = async (row) => {
  try {
    const projectId = projectStore.currentProjectId
    await dismissExperimentSuggestion(projectId, row.suggestion_id)
    ElMessage.success('已忽略')
    await loadSuggestions()
  } catch (e) {
    // Error handled by interceptor
  }
}

const createFromSuggestion = (row) => {
  form.value = {
    name: row.title,
    experiment_type: row.suggested_type,
    hypothesis: row.rationale,
    duration_days: 14,
    confidence_level: 0.95,
    min_detectable_effect: 0.05,
    variants: row.suggested_variants.map((v, i) => ({
      variant_id: `v${i}`,
      name: v,
      weight: 1.0 / row.suggested_variants.length,
      is_control: i === 0,
    })),
    goals: row.suggested_metrics.map(m => ({
      metric_name: m,
      target_value: 0,
      direction: 'increase',
      priority: 1,
    })),
  }
  showCreateDialog.value = true
}

onMounted(() => {
  loadExperiments()
  loadSuggestions()
})
</script>
