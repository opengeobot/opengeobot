<template>
  <div class="space-y-4">
    <el-row :gutter="16">
      <el-col :span="8" v-for="metric in metrics" :key="metric.key">
        <el-card>
          <div class="text-center">
            <p class="text-gray-500">{{ metric.name }}</p>
            <p class="text-4xl font-bold mt-2" :class="metric.color">{{ metric.value }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">洞察列表</h3>
      </template>
      <el-table :data="insights" v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="300" />
        <el-table-column prop="category" label="类别" width="150" />
        <el-table-column prop="priority_score" label="优先级" width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.priority_score * 100" :show-text="false" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="generatePlaybook(row)">生成方案</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">引用来源</h3>
      </template>
      <el-table :data="citationSources" v-loading="loading">
        <el-table-column prop="domain" label="域名" />
        <el-table-column prop="count" label="引用次数" width="120" />
        <el-table-column prop="is_official" label="官方" width="80">
          <template #default="{ row }">
            <el-tag :type="row.is_official ? 'success' : 'info'">{{ row.is_official ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quality_score" label="质量分" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getInsights, getCitationSources, generatePlaybook as generatePlaybookApi } from '@/api'
import { ElMessage } from 'element-plus'

const projectStore = useProjectStore()
const insights = ref([])
const citationSources = ref([])
const loading = ref(false)

const metrics = computed(() => {
  const overview = projectStore.currentProject?._overview || {}
  const m = overview.latest_metrics || {}
  return [
    { key: 'mention_rate', name: '提及率', value: (m.mention_rate || 0).toFixed(2), color: 'text-blue-600' },
    { key: 'citation_rate', name: '引用率', value: (m.citation_rate || 0).toFixed(2), color: 'text-green-600' },
    { key: 'average_position', name: '平均位置', value: (m.average_position || 0).toFixed(2), color: 'text-orange-600' },
    { key: 'sentiment_score', name: '情感分', value: (m.sentiment_score || 0).toFixed(2), color: 'text-purple-600' },
    { key: 'share_of_voice', name: '声量份额', value: (m.share_of_voice || 0).toFixed(2), color: 'text-pink-600' },
    { key: 'citation_quality', name: '引用质量', value: (m.citation_quality || 0).toFixed(2), color: 'text-indigo-600' },
  ]
})

const loadInsights = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    const res = await getInsights(projectStore.currentProjectId)
    insights.value = res.items || []
    citationSources.value = await getCitationSources(projectStore.currentProjectId)
  } finally {
    loading.value = false
  }
}

const generatePlaybook = async (insight) => {
  try {
    await generatePlaybookApi(projectStore.currentProjectId, { insight_id: insight.insight_id })
    ElMessage.success('方案生成成功')
  } catch (error) {
    console.error(error)
  }
}

onMounted(loadInsights)
</script>
