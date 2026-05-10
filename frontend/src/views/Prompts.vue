<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">提示词库</h3>
          <div class="space-x-2">
            <el-button type="primary" @click="showImportDialog = true">批量导入</el-button>
            <el-button type="success" @click="generatePrompts">AI 生成</el-button>
          </div>
        </div>
      </template>
      
      <el-table :data="prompts" v-loading="loading">
        <el-table-column prop="content" label="内容" min-width="300" />
        <el-table-column prop="topic" label="主题" width="120" />
        <el-table-column prop="stage" label="阶段" width="120" />
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="{ row }">
            <el-rate v-model="row.priority" disabled />
          </template>
        </el-table-column>
        <el-table-column prop="language" label="语言" width="100" />
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="updatePrompt(row)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showImportDialog" title="批量导入提示词" width="600px">
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="每行一个提示词" />
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="importPrompts">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getPrompts, importPrompts as importPromptsApi, generatePrompts as generatePromptsApi, updatePrompt as updatePromptApi } from '@/api'
import { ElMessage } from 'element-plus'

const projectStore = useProjectStore()
const prompts = ref([])
const loading = ref(false)
const showImportDialog = ref(false)
const importText = ref('')

const loadPrompts = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    const res = await getPrompts(projectStore.currentProjectId)
    prompts.value = res.items || res
  } finally {
    loading.value = false
  }
}

const importPrompts = async () => {
  const items = importText.value.split('\n').filter(Boolean).map(content => ({
    content,
    language: 'zh-CN',
    region: 'CN',
    topic: 'general',
    stage: 'consideration',
    priority: 3,
  }))
  try {
    await importPromptsApi(projectStore.currentProjectId, { items })
    showImportDialog.value = false
    await loadPrompts()
    ElMessage.success('导入成功')
  } catch (error) {
    console.error(error)
  }
}

const generatePrompts = async () => {
  try {
    await generatePromptsApi(projectStore.currentProjectId, { count: 20 })
    await loadPrompts()
    ElMessage.success('生成成功')
  } catch (error) {
    console.error(error)
  }
}

const updatePrompt = async (prompt) => {
  try {
    await updatePromptApi(projectStore.currentProjectId, prompt.prompt_id, { enabled: prompt.enabled })
  } catch (error) {
    console.error(error)
  }
}

onMounted(loadPrompts)
</script>
