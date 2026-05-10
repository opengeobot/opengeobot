<template>
  <div class="space-y-4">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>
            <h3 class="text-lg font-semibold">机会列表</h3>
          </template>
          <el-table :data="opportunities" v-loading="loading">
            <el-table-column prop="title" label="机会点" min-width="250" />
            <el-table-column prop="category" label="类别" width="150" />
            <el-table-column prop="priority_score" label="优先级" width="120">
              <template #default="{ row }">
                {{ (row.priority_score * 100).toFixed(0) }}%
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="generatePlaybook(row)">生成方案</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <h3 class="text-lg font-semibold">Playbook 列表</h3>
          </template>
          <el-table :data="playbooks" v-loading="loading">
            <el-table-column prop="playbook_type" label="类型" width="150" />
            <el-table-column prop="risk_level" label="风险等级" width="100">
              <template #default="{ row }">
                <el-tag :type="row.risk_level === 'high' ? 'danger' : 'warning'">{{ row.risk_level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="创建时间" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" @click="generatePR(row)">生成 PR</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <h3 class="text-lg font-semibold">PR 草稿列表</h3>
      </template>
      <el-table :data="prDrafts" v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="300" />
        <el-table-column prop="repo_url" label="仓库" width="250" />
        <el-table-column prop="status" label="状态" width="150">
          <template #default="{ row }">
            <el-tag>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="approveDraft(row)" :disabled="row.status !== 'pending_approval'">通过</el-button>
            <el-button size="small" type="danger" @click="rejectDraft(row)" :disabled="row.status !== 'pending_approval'">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getInsights, generatePlaybook as generatePlaybookApi, getPRDrafts, createPRDraft, approvePRDraft, rejectPRDraft } from '@/api'
import { ElMessage } from 'element-plus'

const projectStore = useProjectStore()
const opportunities = ref([])
const playbooks = ref([])
const prDrafts = ref([])
const loading = ref(false)

const loadData = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    opportunities.value = await getInsights(projectStore.currentProjectId)
    prDrafts.value = await getPRDrafts(projectStore.currentProjectId)
  } finally {
    loading.value = false
  }
}

const generatePlaybook = async (insight) => {
  try {
    await generatePlaybookApi(projectStore.currentProjectId, { insight_id: insight.insight_id })
    ElMessage.success('Playbook 生成成功')
    await loadData()
  } catch (error) {
    console.error(error)
  }
}

const generatePR = async (playbook) => {
  try {
    await createPRDraft(projectStore.currentProjectId, {
      playbook_id: playbook.playbook_id,
      repo_url: projectStore.currentProject?.source_url || '',
    })
    ElMessage.success('PR 草稿生成成功')
    await loadData()
  } catch (error) {
    console.error(error)
  }
}

const approveDraft = async (draft) => {
  try {
    await approvePRDraft(projectStore.currentProjectId, draft.draft_id, { comment: 'Approved' })
    ElMessage.success('PR 草稿已通过')
    await loadData()
  } catch (error) {
    console.error(error)
  }
}

const rejectDraft = async (draft) => {
  try {
    await rejectPRDraft(projectStore.currentProjectId, draft.draft_id, { comment: 'Rejected' })
    ElMessage.success('PR 草稿已拒绝')
    await loadData()
  } catch (error) {
    console.error(error)
  }
}

onMounted(loadData)
</script>
