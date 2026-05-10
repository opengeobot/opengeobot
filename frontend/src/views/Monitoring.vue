<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">告警列表</h3>
          <el-button type="primary" @click="runMonitor">运行监控</el-button>
        </div>
      </template>
      
      <el-table :data="alerts" v-loading="loading">
        <el-table-column prop="alert_type" label="告警类型" width="200" />
        <el-table-column prop="fingerprint" label="指纹" width="250" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="last_seen_at" label="最后发现" width="180" />
        <el-table-column prop="assignee" label="负责人" width="120" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="updateAlertStatus(row, 'resolved')">解决</el-button>
            <el-button size="small" @click="updateAlertStatus(row, 'closed')">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">调度任务</h3>
      </template>
      <el-table :data="schedules" v-loading="loading">
        <el-table-column prop="job_id" label="任务 ID" min-width="250" />
        <el-table-column prop="trigger" label="触发器" width="200" />
        <el-table-column prop="next_run_time" label="下次执行" width="200" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="deleteSchedule(row.job_id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getAlerts, updateAlert as updateAlertApi, runMonitor as runMonitorApi, getSchedules, deleteSchedule as deleteScheduleApi } from '@/api'
import { ElMessage } from 'element-plus'

const projectStore = useProjectStore()
const alerts = ref([])
const schedules = ref([])
const loading = ref(false)

const getStatusType = (status) => {
  const map = { open: 'danger', resolved: 'success', closed: 'info' }
  return map[status] || 'info'
}

const loadData = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    const alertsRes = await getAlerts(projectStore.currentProjectId)
    const schedulesRes = await getSchedules()
    // getSchedules returns {schedules: [...], total: 0}
    alerts.value = Array.isArray(alertsRes) ? alertsRes : []
    schedules.value = schedulesRes?.schedules || schedulesRes?.items || []
  } finally {
    loading.value = false
  }
}

const updateAlertStatus = async (alert, status) => {
  try {
    await updateAlertApi(projectStore.currentProjectId, alert.alert_id, { status })
    await loadData()
    ElMessage.success('告警状态已更新')
  } catch (error) {
    console.error(error)
  }
}

const runMonitor = async () => {
  ElMessage.info('监控运行中...')
  // Would need a run_id here in real usage
}

const deleteSchedule = async (jobId) => {
  try {
    await deleteScheduleApi(jobId)
    await loadData()
    ElMessage.success('调度任务已删除')
  } catch (error) {
    console.error(error)
  }
}

onMounted(loadData)
</script>
