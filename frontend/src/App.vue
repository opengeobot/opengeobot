<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useProjectStore } from '@/store/project'

const projectStore = useProjectStore()

onMounted(async () => {
  await projectStore.loadProjects()
})
</script>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
</style>
