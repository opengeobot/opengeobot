<template>
  <div class="space-y-6">
    <!-- 策略模板列表 -->
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">策略模板库</h3>
          <el-button type="primary" @click="showCreateDialog = true">新建模板</el-button>
        </div>
      </template>

      <div class="mb-4 flex gap-4">
        <el-select v-model="filterCategory" placeholder="分类筛选" clearable @change="loadTemplates" style="width: 150px">
          <el-option label="SEO" value="seo" />
          <el-option label="内容" value="content" />
          <el-option label="技术" value="technical" />
          <el-option label="链接建设" value="link_building" />
          <el-option label="用户体验" value="user_experience" />
        </el-select>
        <el-select v-model="filterEffectiveness" placeholder="效果筛选" clearable @change="loadTemplates" style="width: 180px">
          <el-option label="非常有效" value="highly_effective" />
          <el-option label="有效" value="effective" />
          <el-option label="中等有效" value="moderately_effective" />
          <el-option label="无效" value="ineffective" />
        </el-select>
      </div>

      <el-table :data="templates" v-loading="loading" stripe>
        <el-table-column prop="template_id" label="ID" width="100" />
        <el-table-column prop="name" label="模板名称" min-width="150" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="effectiveness" label="效果" width="120">
          <template #default="{ row }">
            <el-tag :type="getEffectivenessType(row.effectiveness)" size="small">{{ getEffectivenessLabel(row.effectiveness) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="usage_count" label="使用次数" width="100" />
        <el-table-column prop="success_rate" label="成功率" width="100">
          <template #default="{ row }">
            {{ (row.success_rate * 100).toFixed(0) }}%
          </template>
        </el-table-column>
        <el-table-column prop="is_public" label="公开" width="80">
          <template #default="{ row }">
            <el-tag :type="row.is_public ? 'success' : 'info'" size="small">{{ row.is_public ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="applyTemplate(row)">应用到项目</el-button>
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 策略实例 -->
    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">我的策略实例</h3>
      </template>

      <el-table :data="instances" v-loading="instanceLoading" stripe>
        <el-table-column prop="instance_id" label="ID" width="100" />
        <el-table-column prop="template_id" label="模板 ID" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getInstanceStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="started_at" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.started_at) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="updateInstanceStatus(row, 'completed')">完成</el-button>
            <el-button size="small" @click="updateInstanceStatus(row, 'paused')">暂停</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Benchmark 分析 -->
    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">行业 Benchmark</h3>
      </template>

      <div class="mb-4">
        <el-select v-model="benchmarkIndustry" placeholder="选择行业" style="width: 200px">
          <el-option label="科技" value="technology" />
          <el-option label="电商" value="ecommerce" />
          <el-option label="金融" value="finance" />
          <el-option label="教育" value="education" />
          <el-option label="媒体" value="media" />
        </el-select>
        <el-button type="primary" @click="runBenchmark" :loading="benchmarkLoading" class="ml-2">运行分析</el-button>
      </div>

      <div v-if="benchmark" class="space-y-4">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="综合得分">{{ benchmark.overall_score.toFixed(1) }}/100</el-descriptions-item>
          <el-descriptions-item label="行业">{{ getIndustryLabel(benchmark.industry) }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="benchmark.strengths.length > 0">
          <h4 class="font-semibold text-green-600">优势</h4>
          <ul class="list-disc pl-5 space-y-1">
            <li v-for="(s, idx) in benchmark.strengths" :key="idx" class="text-sm text-green-600">{{ s }}</li>
          </ul>
        </div>

        <div v-if="benchmark.weaknesses.length > 0">
          <h4 class="font-semibold text-red-600">劣势</h4>
          <ul class="list-disc pl-5 space-y-1">
            <li v-for="(w, idx) in benchmark.weaknesses" :key="idx" class="text-sm text-red-600">{{ w }}</li>
          </ul>
        </div>

        <div v-if="benchmark.recommendations.length > 0">
          <h4 class="font-semibold text-blue-600">建议</h4>
          <ul class="list-disc pl-5 space-y-1">
            <li v-for="(r, idx) in benchmark.recommendations" :key="idx" class="text-sm">{{ r }}</li>
          </ul>
        </div>
      </div>
    </el-card>

    <!-- 创建/编辑模板对话框 -->
    <el-dialog v-model="showCreateDialog" :title="editingTemplate ? '编辑模板' : '新建模板'" width="700px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="模板名称">
          <el-input v-model="form.name" placeholder="输入模板名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="SEO" value="seo" />
            <el-option label="内容" value="content" />
            <el-option label="技术" value="technical" />
            <el-option label="链接建设" value="link_building" />
            <el-option label="用户体验" value="user_experience" />
            <el-option label="社交" value="social" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用场景">
          <el-input v-model="form.applicable_scenarios_str" placeholder="多个场景用逗号分隔" />
        </el-form-item>
        <el-form-item label="步骤">
          <el-input v-model="form.steps_str" type="textarea" :rows="4" placeholder="每行一个步骤" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags_str" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="form.is_public" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ editingTemplate ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 应用模板对话框 -->
    <el-dialog v-model="showApplyDialog" title="应用到项目" width="400px">
      <p>确定要将模板 "{{ selectedTemplate?.name }}" 应用到当前项目吗？</p>
      <template #footer>
        <el-button @click="showApplyDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmApply" :loading="applying">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getStrategyTemplates,
  createStrategyTemplate,
  updateStrategyTemplate,
  deleteStrategyTemplate,
  getStrategyInstances,
  createStrategyInstance,
  updateStrategyInstanceStatus,
  getProjectBenchmark,
  analyzeBenchmark,
} from '@/api'

const projectStore = useProjectStore()
const templates = ref([])
const instances = ref([])
const benchmark = ref(null)
const loading = ref(false)
const instanceLoading = ref(false)
const benchmarkLoading = ref(false)
const submitting = ref(false)
const applying = ref(false)
const showCreateDialog = ref(false)
const showApplyDialog = ref(false)
const editingTemplate = ref(null)
const selectedTemplate = ref(null)

const filterCategory = ref('')
const filterEffectiveness = ref('')
const benchmarkIndustry = ref('technology')

const form = ref({
  name: '',
  description: '',
  category: 'seo',
  applicable_scenarios_str: '',
  steps_str: '',
  tags_str: '',
  is_public: true,
})

const getCategoryLabel = (cat) => {
  const map = { seo: 'SEO', content: '内容', technical: '技术', link_building: '链接建设', user_experience: '用户体验', social: '社交', other: '其他' }
  return map[cat] || cat
}

const getEffectivenessLabel = (eff) => {
  const map = { highly_effective: '非常有效', effective: '有效', moderately_effective: '中等有效', ineffective: '无效', unknown: '未知' }
  return map[eff] || eff
}

const getEffectivenessType = (eff) => {
  const map = { highly_effective: 'success', effective: '', moderately_effective: 'warning', ineffective: 'danger', unknown: 'info' }
  return map[eff] || 'info'
}

const getInstanceStatusType = (status) => {
  const map = { active: 'success', paused: 'warning', completed: 'info', failed: 'danger' }
  return map[status] || 'info'
}

const getIndustryLabel = (ind) => {
  const map = { technology: '科技', ecommerce: '电商', finance: '金融', education: '教育', media: '媒体' }
  return map[ind] || ind
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const loadTemplates = async () => {
  loading.value = true
  try {
    const params = {}
    if (filterCategory.value) params.category = filterCategory.value
    if (filterEffectiveness.value) params.effectiveness = filterEffectiveness.value
    const res = await getStrategyTemplates(params)
    templates.value = res.templates || []
  } catch (e) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}

const loadInstances = async () => {
  instanceLoading.value = true
  try {
    const projectId = projectStore.currentProjectId
    if (!projectId) return
    const res = await getStrategyInstances(projectId)
    instances.value = res.instances || []
  } catch (e) {
    // Error handled by interceptor
  } finally {
    instanceLoading.value = false
  }
}

const loadBenchmark = async () => {
  try {
    const projectId = projectStore.currentProjectId
    if (!projectId) return
    benchmark.value = await getProjectBenchmark(projectId)
  } catch (e) {
    // Error handled by interceptor
  }
}

const handleSubmit = async () => {
  if (!form.value.name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  submitting.value = true
  try {
    const data = {
      name: form.value.name,
      description: form.value.description,
      category: form.value.category,
      applicable_scenarios: form.value.applicable_scenarios_str ? form.value.applicable_scenarios_str.split(',').map(s => s.trim()) : [],
      steps: form.value.steps_str ? form.value.steps_str.split('\n').filter(s => s.trim()) : [],
      tags: form.value.tags_str ? form.value.tags_str.split(',').map(s => s.trim()) : [],
      is_public: form.value.is_public,
    }
    if (editingTemplate.value) {
      await updateStrategyTemplate(editingTemplate.value.template_id, data)
      ElMessage.success('更新成功')
    } else {
      await createStrategyTemplate(data)
      ElMessage.success('创建成功')
    }
    showCreateDialog.value = false
    editingTemplate.value = null
    form.value = { name: '', description: '', category: 'seo', applicable_scenarios_str: '', steps_str: '', tags_str: '', is_public: true }
    await loadTemplates()
  } catch (e) {
    // Error handled by interceptor
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除模板 "${row.name}" 吗？`, '确认删除', { type: 'warning' })
    await deleteStrategyTemplate(row.template_id)
    ElMessage.success('删除成功')
    await loadTemplates()
  } catch (e) {
    if (e !== 'cancel') {}
  }
}

const handleEdit = (row) => {
  editingTemplate.value = row
  form.value = {
    name: row.name,
    description: row.description,
    category: row.category,
    applicable_scenarios_str: (row.applicable_scenarios || []).join(', '),
    steps_str: (row.steps || []).join('\n'),
    tags_str: (row.tags || []).join(', '),
    is_public: row.is_public,
  }
  showCreateDialog.value = true
}

const applyTemplate = (row) => {
  selectedTemplate.value = row
  showApplyDialog.value = true
}

const confirmApply = async () => {
  applying.value = true
  try {
    const projectId = projectStore.currentProjectId
    await createStrategyInstance(projectId, {
      template_id: selectedTemplate.value.template_id,
      project_id: projectId,
    })
    ElMessage.success('应用成功')
    showApplyDialog.value = false
    selectedTemplate.value = null
    await loadInstances()
  } catch (e) {
    // Error handled by interceptor
  } finally {
    applying.value = false
  }
}

const updateInstanceStatus = async (row, status) => {
  try {
    const projectId = projectStore.currentProjectId
    await updateStrategyInstanceStatus(projectId, row.instance_id, status)
    ElMessage.success(`状态已更新为 ${status}`)
    await loadInstances()
  } catch (e) {
    // Error handled by interceptor
  }
}

const runBenchmark = async () => {
  benchmarkLoading.value = true
  try {
    const projectId = projectStore.currentProjectId
    // 使用示例项目指标
    const projectMetrics = {
      indexed_pages: 100,
      crawl_rate: 0.85,
      mention_rate: 0.15,
    }
    await analyzeBenchmark({ project_id: projectId, industry: benchmarkIndustry.value }, projectMetrics)
    ElMessage.success('Benchmark 分析完成')
    await loadBenchmark()
  } catch (e) {
    // Error handled by interceptor
  } finally {
    benchmarkLoading.value = false
  }
}

onMounted(() => {
  loadTemplates()
  loadInstances()
  loadBenchmark()
})
</script>
