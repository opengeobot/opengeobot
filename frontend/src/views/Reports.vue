<template>
  <div class="space-y-4">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="flex justify-between items-center">
              <h3 class="text-lg font-semibold">验证报告</h3>
              <el-button type="primary" @click="showCreateDialog = true">创建对比</el-button>
            </div>
          </template>
          <el-table :data="verificationReports" v-loading="loading">
            <el-table-column prop="report_id" label="ID" width="200" />
            <el-table-column prop="baseline_run_id" label="基线 Run" width="180" />
            <el-table-column prop="after_run_id" label="验证 Run" width="180" />
            <el-table-column prop="summary" label="总结" min-width="200" />
            <el-table-column prop="created_at" label="创建时间" width="180" />
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <h3 class="text-lg font-semibold">稳定性报告</h3>
          </template>
          <el-table :data="stabilityReports" v-loading="loading">
            <el-table-column prop="report_id" label="ID" width="200" />
            <el-table-column prop="repeats" label="重复次数" width="100" />
            <el-table-column prop="run_type" label="类型" width="120" />
            <el-table-column prop="created_at" label="创建时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" @click="viewStability(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">周报预览</h3>
      </template>
      <div v-if="weeklyReport">
        <h4>{{ weeklyReport.subject }}</h4>
        <pre class="bg-gray-50 p-4 mt-2 rounded">{{ JSON.stringify(weeklyReport.body, null, 2) }}</pre>
      </div>
    </el-card>

    <el-dialog v-model="showCreateDialog" title="创建验证对比" width="500px">
      <el-form :model="newVerification" label-width="100px">
        <el-form-item label="基线 Run">
          <el-select v-model="newVerification.baseline_run_id" style="width: 100%">
            <el-option v-for="run in runs" :key="run.run_id" :label="run.run_id" :value="run.run_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="验证 Run">
          <el-select v-model="newVerification.after_run_id" style="width: 100%">
            <el-option v-for="run in runs" :key="run.run_id" :label="run.run_id" :value="run.run_id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createVerification">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getStabilityReports, getWeeklyReport, getRuns, createVerification as createVerificationApi } from '@/api'

const projectStore = useProjectStore()
const verificationReports = ref([])
const stabilityReports = ref([])
const weeklyReport = ref(null)
const runs = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)

const newVerification = ref({
  baseline_run_id: '',
  after_run_id: '',
})

const loadData = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    stabilityReports.value = await getStabilityReports(projectStore.currentProjectId)
    weeklyReport.value = await getWeeklyReport(projectStore.currentProjectId)
    runs.value = await getRuns(projectStore.currentProjectId)
  } finally {
    loading.value = false
  }
}

const createVerification = async () => {
  try {
    await createVerificationApi(projectStore.currentProjectId, newVerification.value)
    showCreateDialog.value = false
    await loadData()
  } catch (error) {
    console.error(error)
  }
}

const viewStability = (report) => {
  console.log('Viewing stability report:', report)
}

onMounted(loadData)
</script>
