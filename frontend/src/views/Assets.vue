<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex justify-between items-center">
          <h3 class="text-lg font-semibold">资产列表</h3>
          <div class="space-x-2">
            <el-button type="primary" @click="showCreateDialog = true">添加资产</el-button>
            <el-button @click="syncAssets">同步资产</el-button>
          </div>
        </div>
      </template>
      
      <el-table :data="assets" v-loading="loading">
        <el-table-column prop="asset_type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.asset_type === 'website' ? '网站' : '仓库' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="source_url" label="URL" />
        <el-table-column prop="title" label="标题" width="200" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="last_crawled_at" label="最后抓取" width="180" />
        <el-table-column prop="content_version" label="版本" width="100" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="deleteAsset(row.asset_id)" type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showCreateDialog" title="添加资产" width="500px">
      <el-form :model="newAsset" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="newAsset.asset_type" style="width: 100%">
            <el-option label="网站" value="website" />
            <el-option label="仓库" value="repository" />
          </el-select>
        </el-form-item>
        <el-form-item label="URL">
          <el-input v-model="newAsset.source_url" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createAsset">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useProjectStore } from '@/store/project'
import { getAssets, createAsset as createAssetApi, deleteAsset as deleteAssetApi, syncAssets as syncAssetsApi } from '@/api'
import { ElMessage } from 'element-plus'

const projectStore = useProjectStore()
const assets = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)

const newAsset = ref({
  asset_type: 'website',
  source_url: '',
})

const getStatusType = (status) => {
  const map = { pending: 'info', success: 'success', failed: 'danger' }
  return map[status] || 'info'
}

const loadAssets = async () => {
  if (!projectStore.currentProjectId) return
  loading.value = true
  try {
    const res = await getAssets(projectStore.currentProjectId)
    assets.value = res.items || res
  } finally {
    loading.value = false
  }
}

const createAsset = async () => {
  if (!projectStore.currentProjectId) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    await createAssetApi(projectStore.currentProjectId, newAsset.value)
    showCreateDialog.value = false
    await loadAssets()
    ElMessage.success('资产创建成功')
  } catch (error) {
    console.error(error)
  }
}

const deleteAsset = async (assetId) => {
  if (!projectStore.currentProjectId) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    await deleteAssetApi(projectStore.currentProjectId, assetId)
    await loadAssets()
    ElMessage.success('资产删除成功')
  } catch (error) {
    console.error(error)
  }
}

const syncAssets = async () => {
  if (!projectStore.currentProjectId) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    await syncAssetsApi(projectStore.currentProjectId, { force: false })
    await loadAssets()
    ElMessage.success('资产同步成功')
  } catch (error) {
    console.error(error)
  }
}

onMounted(loadAssets)
</script>
