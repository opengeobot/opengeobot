<template>
  <div class="flex h-screen bg-gray-100">
    <!-- Sidebar -->
    <aside 
      :class="[
        'bg-white shadow-lg transition-all duration-300',
        sidebarCollapsed ? 'w-16' : 'w-64'
      ]"
    >
      <div class="flex flex-col h-full">
        <!-- Logo -->
        <div class="flex items-center justify-between h-16 px-4 bg-blue-600 text-white">
          <h1 v-if="!sidebarCollapsed" class="text-lg font-bold">OpenGEO Bot</h1>
          <h1 v-else class="text-lg font-bold mx-auto">OG</h1>
          <button 
            @click="sidebarCollapsed = !sidebarCollapsed"
            class="text-white hover:bg-blue-700 rounded p-1"
          >
            <component :is="sidebarCollapsed ? 'Expand' : 'Fold'" />
          </button>
        </div>

        <!-- Navigation -->
        <nav class="flex-1 overflow-y-auto py-4">
          <router-link
            v-for="item in menuItems"
            :key="item.path"
            :to="item.path"
            :class="[
              'flex items-center px-4 py-3 text-gray-700 hover:bg-blue-50 hover:text-blue-600 transition-colors',
              $route.path === item.path ? 'bg-blue-50 text-blue-600 border-r-4 border-blue-600' : ''
            ]"
          >
            <component :is="item.icon" class="w-5 h-5" />
            <span v-if="!sidebarCollapsed" class="ml-3">{{ item.name }}</span>
          </router-link>
        </nav>
      </div>
    </aside>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- Header -->
      <header class="bg-white shadow-sm h-16 flex items-center justify-between px-6">
        <div class="flex items-center space-x-4">
          <h2 class="text-xl font-semibold text-gray-800">{{ $route.meta.title }}</h2>
        </div>
        
        <div class="flex items-center space-x-4">
          <!-- Project Selector -->
          <el-select
            v-model="selectedProjectId"
            @change="handleProjectChange"
            placeholder="选择项目"
            class="w-48"
          >
            <el-option
              v-for="project in projectStore.projects"
              :key="project.project_id"
              :label="project.project_name"
              :value="project.project_id"
            />
          </el-select>

          <!-- Language Switcher -->
          <el-button @click="toggleLanguage" circle>
            {{ currentLanguage === 'zh-CN' ? 'EN' : '中文' }}
          </el-button>
        </div>
      </header>

      <!-- Page Content -->
      <main class="flex-1 overflow-y-auto p-6">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/store/project'
import {
  House,
  Files,
  ChatDotRound,
    Cpu,
  TrendCharts,
  Monitor,
  Document,
  Bell,
  Collection,
  Expand,
  Fold,
  Link,
  MagicStick,
  FolderOpened,
  Setting,
} from '@element-plus/icons-vue'

const { locale } = useI18n()
const router = useRouter()
const projectStore = useProjectStore()

// 加载项目列表
onMounted(async () => {
  await projectStore.loadProjects()
})

const sidebarCollapsed = ref(false)
const selectedProjectId = computed({
  get: () => projectStore.currentProjectId,
  set: (val) => val,
})

const currentLanguage = computed(() => locale.value)

const menuItems = [
  { path: '/', name: '项目概览', icon: House },
  { path: '/assets', name: '资产中心', icon: Files },
  { path: '/prompts', name: '提示词库', icon: ChatDotRound },
  { path: '/runs', name: '运行中心', icon: Cpu },
  { path: '/insights', name: '洞察中心', icon: TrendCharts },
  { path: '/bot', name: 'Bot 工作台', icon: Monitor },
  { path: '/reports', name: '效果报告', icon: Document },
  { path: '/monitoring', name: '监控告警', icon: Bell },
  { path: '/strategy-memory', name: '策略记忆库', icon: Collection },
  // Phase 3 新增
  { path: '/reference-tasks', name: '引用建设', icon: Link },
  { path: '/experiments', name: '实验设计', icon: MagicStick },
  { path: '/strategy-templates', name: '策略模板库', icon: FolderOpened },
  { path: '/tenant-management', name: '多租户管理', icon: Setting },
]

const handleProjectChange = (projectId) => {
  const project = projectStore.projects.find(p => p.project_id === projectId)
  if (project) {
    projectStore.setCurrentProject(project)
    router.push('/')
  }
}

const toggleLanguage = () => {
  locale.value = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
}
</script>
