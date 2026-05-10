<template>
  <div class="space-y-6">
    <!-- Project Selector Banner -->
    <el-card v-if="!projectStore.currentProject" class="bg-blue-50">
      <div class="text-center">
        <el-empty description="请先选择或创建一个项目" />
        <el-button type="primary" @click="showCreateDialog = true" class="mt-4">
          创建新项目
        </el-button>
      </div>
    </el-card>

    <template v-else>
      <!-- North Star Metric -->
      <el-card>
        <template #header>
          <div class="flex justify-between items-center">
            <h3 class="text-lg font-semibold">{{ $t('overview.northStar') }}</h3>
            <el-tag type="success" size="large">+15.2%</el-tag>
          </div>
        </template>
        <div class="h-64">
          <v-chart :option="trendChartOption" autoresize />
        </div>
      </el-card>

      <!-- Core Metrics -->
      <el-row :gutter="16">
        <el-col :span="6" v-for="metric in coreMetrics" :key="metric.key">
          <el-card>
            <div class="text-center">
              <p class="text-gray-500 text-sm">{{ metric.name }}</p>
              <p :class="['text-3xl font-bold mt-2', metric.color]">{{ metric.value }}</p>
              <p :class="['text-sm mt-1', metric.trend > 0 ? 'text-green-500' : 'text-red-500']">
                {{ metric.trend > 0 ? '↑' : '↓' }} {{ Math.abs(metric.trend) }}%
              </p>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Recent Alerts and Top Opportunities -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card>
            <template #header>
              <h3 class="text-lg font-semibold">{{ $t('overview.recentAlerts') }}</h3>
            </template>
            <el-table :data="alerts" style="width: 100%">
              <el-table-column prop="alert_type" label="类型" />
              <el-table-column prop="status" label="状态">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="last_seen_at" label="最后发现" />
            </el-table>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card>
            <template #header>
              <h3 class="text-lg font-semibold">{{ $t('overview.topOpportunities') }}</h3>
            </template>
            <el-table :data="opportunities" style="width: 100%">
              <el-table-column prop="title" label="机会点" />
              <el-table-column prop="category" label="类别" />
              <el-table-column prop="priority_score" label="优先级">
                <template #default="{ row }">
                  <el-progress :percentage="row.priority_score * 100" :show-text="false" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <!-- Recent Runs -->
      <el-card>
        <template #header>
          <div class="flex justify-between items-center">
            <h3 class="text-lg font-semibold">{{ $t('overview.recentRuns') }}</h3>
            <el-button type="primary" @click="$router.push('/runs')">查看所有</el-button>
          </div>
        </template>
        <el-table :data="recentRuns" style="width: 100%">
          <el-table-column prop="run_id" label="Run ID" width="200" />
          <el-table-column prop="run_type" label="类型" />
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="started_at" label="开始时间" />
          <el-table-column label="指标">
            <template #default="{ row }">
              <span class="text-xs">MR: {{ row.metrics?.mention_rate || 0 }}</span>
              <span class="text-xs ml-2">CR: {{ row.metrics?.citation_rate || 0 }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- Create Project Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建新项目" width="500px">
      <el-form :model="newProject" label-width="100px">
        <el-form-item label="项目名称">
          <el-input v-model="newProject.project_name" />
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="newProject.project_type" style="width: 100%">
            <el-option label="网站" value="website" />
            <el-option label="仓库" value="repository" />
          </el-select>
        </el-form-item>
        <el-form-item label="URL">
          <el-input v-model="newProject.source_url" />
        </el-form-item>
        <el-form-item label="品牌名称">
          <el-input v-model="newProject.brand_name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateProject">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { useProjectStore } from '@/store/project'
import { getAlerts, getInsights, getRuns } from '@/api'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const { t } = useI18n()
const projectStore = useProjectStore()

const showCreateDialog = ref(false)
const alerts = ref([])
const opportunities = ref([])
const recentRuns = ref([])

const newProject = ref({
  project_name: '',
  project_type: 'website',
  source_url: '',
  brand_name: '',
  language: 'zh-CN',
  region: 'CN',
  competitors: [],
})

const coreMetrics = computed(() => {
  const overview = projectStore.currentProject?._overview || {}
  const metrics = overview.latest_metrics || {}
  const trend = overview.trend || {}
  
  return [
    { key: 'mention_rate', name: t('metrics.mentionRate'), value: (metrics.mention_rate || 0).toFixed(2), trend: trend.mention_rate_delta || 0, color: 'text-blue-600' },
    { key: 'citation_rate', name: t('metrics.citationRate'), value: (metrics.citation_rate || 0).toFixed(2), trend: trend.citation_rate_delta || 0, color: 'text-green-600' },
    { key: 'average_position', name: t('metrics.averagePosition'), value: (metrics.average_position || 0).toFixed(2), trend: 0, color: 'text-orange-600' },
    { key: 'sentiment_score', name: t('metrics.sentimentScore'), value: (metrics.sentiment_score || 0).toFixed(2), trend: 0, color: 'text-purple-600' },
  ]
})

const trendChartOption = computed(() => ({
  xAxis: {
    type: 'category',
    data: ['1周前', '6天前', '5天前', '4天前', '3天前', '2天前', '今天'],
  },
  yAxis: {
    type: 'value',
  },
  series: [
    {
      name: '提及率',
      data: [0.4, 0.45, 0.5, 0.48, 0.55, 0.6, 0.65],
      type: 'line',
      smooth: true,
    },
    {
      name: '引用率',
      data: [0.3, 0.35, 0.32, 0.4, 0.42, 0.48, 0.5],
      type: 'line',
      smooth: true,
    },
  ],
  tooltip: {
    trigger: 'axis',
  },
  legend: {
    data: ['提及率', '引用率'],
  },
}))

const getStatusType = (status) => {
  const map = {
    success: 'success',
    failed: 'danger',
    running: 'warning',
    pending: 'info',
    partial_success: 'warning',
    open: 'danger',
    resolved: 'success',
    closed: 'info',
  }
  return map[status] || 'info'
}

const handleCreateProject = async () => {
  try {
    await projectStore.createProject(newProject.value)
    showCreateDialog.value = false
  } catch (error) {
    console.error('Failed to create project:', error)
  }
}

onMounted(async () => {
  if (projectStore.currentProjectId) {
    try {
      const [alertsRes, insightsRes, runsRes] = await Promise.all([
        getAlerts(projectStore.currentProjectId, { status: 'open' }),
        getInsights(projectStore.currentProjectId),
        getRuns(projectStore.currentProjectId),
      ])
      alerts.value = (alertsRes || []).slice(0, 5)
      opportunities.value = (insightsRes || []).slice(0, 10)
      recentRuns.value = (runsRes || []).slice(0, 5)
    } catch (error) {
      console.error('Failed to load overview data:', error)
    }
  }
})
</script>
