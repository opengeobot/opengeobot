<template>
  <div class="space-y-4">
    <el-row :gutter="16">
      <el-col :span="6" v-for="stat in stats" :key="stat.key">
        <el-card>
          <div class="text-center">
            <p class="text-gray-500">{{ stat.name }}</p>
            <p class="text-3xl font-bold mt-2" :class="stat.color">{{ stat.value }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">策略记忆库</h3>
      </template>
      
      <el-table :data="memories" v-loading="loading">
        <el-table-column prop="memory_id" label="ID" width="200" />
        <el-table-column prop="playbook_type" label="Playbook 类型" width="150" />
        <el-table-column prop="success" label="成功" width="100">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="impact_metrics" label="影响指标" min-width="200">
          <template #default="{ row }">
            <pre class="text-xs">{{ JSON.stringify(row.impact_metrics, null, 2) }}</pre>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="180" />
      </el-table>
    </el-card>

    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">推荐策略</h3>
      </template>
      <el-alert
        v-for="(rec, index) in recommendations"
        :key="index"
        :title="rec.title"
        :description="rec.description"
        :type="rec.type"
        :closable="false"
        class="mb-2"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getStrategyMemories } from '@/api'

const projectStore = useProjectStore()
const memories = ref([])
const loading = ref(false)

const stats = computed(() => {
  const total = memories.value.length
  const success = memories.value.filter(m => m.success).length
  const rate = total > 0 ? ((success / total) * 100).toFixed(1) : 0
  
  return [
    { key: 'total', name: '总策略数', value: total, color: 'text-blue-600' },
    { key: 'success', name: '成功策略', value: success, color: 'text-green-600' },
    { key: 'failed', name: '失败策略', value: total - success, color: 'text-red-600' },
    { key: 'rate', name: '成功率', value: `${rate}%`, color: 'text-purple-600' },
  ]
})

const recommendations = computed(() => [
  { title: '内容优化策略', description: '基于历史数据，针对技术文档和 FAQ 页面的优化最有效', type: 'success' },
  { title: '结构化数据策略', description: '添加 Schema 标记后，引用率提升了 30%', type: 'warning' },
  { title: '竞品对比策略', description: '在竞品对比页面增加对比表，提及率提升明显', type: 'info' },
])

const loadMemories = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    memories.value = await getStrategyMemories(projectStore.currentProjectId)
  } finally {
    loading.value = false
  }
}

onMounted(loadMemories)
</script>
