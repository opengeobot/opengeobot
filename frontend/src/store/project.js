import { defineStore } from 'pinia'
import { getProjects, createProject, getProjectOverview } from '@/api'

export const useProjectStore = defineStore('project', {
  state: () => ({
    projects: [],
    currentProject: null,
    loading: false,
  }),
  
  getters: {
    currentProjectId: (state) => state.currentProject?.project_id || null,
  },
  
  actions: {
    async loadProjects() {
      this.loading = true
      try {
        const response = await getProjects()
        this.projects = response.items || response
        if (this.projects.length > 0 && !this.currentProject) {
          this.setCurrentProject(this.projects[0])
        }
      } catch (error) {
        console.error('Failed to load projects:', error)
      } finally {
        this.loading = false
      }
    },
    
    async createProject(data) {
      const project = await createProject(data)
      await this.loadProjects()
      return project
    },
    
    setCurrentProject(project) {
      this.currentProject = project
      localStorage.setItem('currentProjectId', project.project_id)
    },
    
    async loadOverview() {
      if (!this.currentProjectId) return null
      this.loading = true
      try {
        return await getProjectOverview(this.currentProjectId)
      } catch (error) {
        console.error('Failed to load overview:', error)
        return null
      } finally {
        this.loading = false
      }
    },
  },
})
