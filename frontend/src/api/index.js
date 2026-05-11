import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor
api.interceptors.request.use(
  (config) => {
    const projectId = localStorage.getItem('currentProjectId')
    if (projectId) {
      config.headers['X-Project-Id'] = projectId
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.detail || '请求失败'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

// Projects
export const getProjects = () => api.get('/projects')
export const createProject = (data) => api.post('/projects', data)
export const getProject = (id) => api.get(`/projects/${id}`)
export const updateProject = (id, data) => api.patch(`/projects/${id}`, data)
export const deleteProject = (id) => api.delete(`/projects/${id}`)
export const getProjectOverview = (id) => api.get(`/projects/${id}/overview`)

// Assets
export const getAssets = (projectId, params = {}) => api.get(`/projects/${projectId}/assets`, { params })
export const createAsset = (projectId, data) => api.post(`/projects/${projectId}/assets`, data)
export const updateAsset = (projectId, assetId, data) => api.patch(`/projects/${projectId}/assets/${assetId}`, data)
export const deleteAsset = (projectId, assetId) => api.delete(`/projects/${projectId}/assets/${assetId}`)
export const syncAssets = (projectId, data) => api.post(`/projects/${projectId}/assets/sync`, data)
export const getAssetChanges = (projectId, params = {}) => api.get(`/projects/${projectId}/asset-changes`, { params })

// Prompts
export const getPrompts = (projectId, params = {}) => api.get(`/projects/${projectId}/prompts`, { params })
export const importPrompts = (projectId, data) => api.post(`/projects/${projectId}/prompts/import`, data)
export const generatePrompts = (projectId, data) => api.post(`/projects/${projectId}/prompts/generate`, data)
export const updatePrompt = (projectId, promptId, data) => api.patch(`/projects/${projectId}/prompts/${promptId}`, data)

// Runs
export const getRuns = (projectId) => api.get(`/projects/${projectId}/runs`)
export const createRun = (projectId, data, asyncMode = false) => api.post(`/projects/${projectId}/runs?async_mode=${asyncMode}`, data)
export const getRun = (projectId, runId) => api.get(`/projects/${projectId}/runs/${runId}`)

// Insights
export const getInsights = (projectId, params = {}) => api.get(`/projects/${projectId}/insights`, { params })
export const generateInsights = (projectId, data) => api.post(`/projects/${projectId}/insights`, data)

// Playbooks
export const generatePlaybook = (projectId, data) => api.post(`/projects/${projectId}/playbooks`, data)

// GitHub PR Drafts
export const createPRDraft = (projectId, data) => api.post(`/projects/${projectId}/github/pr-drafts`, data)
export const getPRDrafts = (projectId, params = {}) => api.get(`/projects/${projectId}/github/pr-drafts`, { params })
export const approvePRDraft = (projectId, draftId, data) => api.post(`/projects/${projectId}/github/pr-drafts/${draftId}/approve`, data)
export const rejectPRDraft = (projectId, draftId, data) => api.post(`/projects/${projectId}/github/pr-drafts/${draftId}/reject`, data)

// Verification
export const createVerification = (projectId, data) => api.post(`/projects/${projectId}/verification`, data)

// Stability Reports
export const getStabilityReports = (projectId) => api.get(`/projects/${projectId}/stability-reports`)
export const createStabilityReport = (projectId, data) => api.post(`/projects/${projectId}/stability-reports`, data)
export const getStabilityReport = (projectId, reportId) => api.get(`/projects/${projectId}/stability-reports/${reportId}`)

// Weekly Report
export const getWeeklyReport = (projectId, params = {}) => api.get(`/projects/${projectId}/weekly-report`, { params })

// Alerts
export const getAlerts = (projectId, params = {}) => api.get(`/projects/${projectId}/alerts`, { params })
export const updateAlert = (projectId, alertId, data) => api.patch(`/projects/${projectId}/alerts/${alertId}`, data)
export const runMonitor = (projectId, runId, params = {}) => api.post(`/projects/${projectId}/monitor/${runId}`, { params })

// Schedules
export const getSchedules = () => api.get('/schedules')
export const createSchedule = (data) => api.post('/schedules', data)
export const getSchedule = (jobId) => api.get(`/schedules/${jobId}`)
export const updateSchedule = (jobId, data) => api.patch(`/schedules/${jobId}`, data)
export const deleteSchedule = (jobId) => api.delete(`/schedules/${jobId}`)

// Strategy Memory
export const getStrategyMemories = (projectId) => api.get(`/projects/${projectId}/strategy-memories`)
export const saveStrategyMemory = (projectId, data) => api.post(`/projects/${projectId}/strategy-memory`, data)

// Citations
export const getCitationSources = (projectId, params = {}) => api.get(`/projects/${projectId}/citations/sources`, { params })

// Engines
export const getEngines = () => api.get('/engines')

// ==================== Phase 3: 多租户与权限 ====================

// Tenants
export const getTenants = (params = {}) => api.get('/tenants', { params })
export const createTenant = (data) => api.post('/tenants', data)
export const getTenant = (id) => api.get(`/tenants/${id}`)
export const updateTenant = (id, data) => api.patch(`/tenants/${id}`, data)
export const deleteTenant = (id) => api.delete(`/tenants/${id}`)

// Users
export const getUsers = (params = {}) => api.get('/users', { params })
export const createUser = (data) => api.post('/users', data)
export const getUser = (id) => api.get(`/users/${id}`)
export const updateUser = (id, data) => api.patch(`/users/${id}`, data)
export const deleteUser = (id) => api.delete(`/users/${id}`)
export const getUserPermissions = (id) => api.get(`/users/${id}/permissions`)
export const getRolePermissions = (role) => api.get(`/roles/${role}/permissions`)

// Audit Logs
export const getAuditLogs = (params = {}) => api.get('/audit-logs', { params })

// ==================== Phase 3.3: 外部引用建设 ====================

// Reference Tasks
export const getReferenceTasks = (projectId, params = {}) => api.get(`/projects/${projectId}/reference-tasks`, { params })
export const createReferenceTask = (projectId, data) => api.post(`/projects/${projectId}/reference-tasks`, data)
export const getReferenceTask = (projectId, taskId) => api.get(`/projects/${projectId}/reference-tasks/${taskId}`)
export const updateReferenceTask = (projectId, taskId, data) => api.patch(`/projects/${projectId}/reference-tasks/${taskId}`, data)
export const deleteReferenceTask = (projectId, taskId) => api.delete(`/projects/${projectId}/reference-tasks/${taskId}`)

// Outreach Records
export const getOutreachRecords = (projectId, taskId) => api.get(`/projects/${projectId}/reference-tasks/${taskId}/outreach`)
export const createOutreachRecord = (projectId, taskId, data) => api.post(`/projects/${projectId}/reference-tasks/${taskId}/outreach`, data)
export const updateOutreachRecord = (projectId, outreachId, data) => api.patch(`/projects/${projectId}/outreach/${outreachId}`, data)

// Tracking Records
export const getTrackingRecords = (projectId, taskId) => api.get(`/projects/${projectId}/reference-tasks/${taskId}/tracking`)
export const createTrackingRecord = (projectId, taskId, data) => api.post(`/projects/${projectId}/reference-tasks/${taskId}/tracking`, data)
export const updateTrackingStatus = (projectId, trackingId, params) => api.patch(`/projects/${projectId}/tracking/${trackingId}`, null, { params })

// Reference Statistics
export const getReferenceStatistics = (projectId) => api.get(`/projects/${projectId}/reference-statistics`)

// ==================== Phase 3.4: 自动实验设计 ====================

// Experiments
export const getExperiments = (projectId, params = {}) => api.get(`/projects/${projectId}/experiments`, { params })
export const createExperiment = (projectId, data) => api.post(`/projects/${projectId}/experiments`, data)
export const getExperiment = (projectId, experimentId) => api.get(`/projects/${projectId}/experiments/${experimentId}`)
export const updateExperiment = (projectId, experimentId, data) => api.patch(`/projects/${projectId}/experiments/${experimentId}`, data)
export const deleteExperiment = (projectId, experimentId) => api.delete(`/projects/${projectId}/experiments/${experimentId}`)

// Experiment Results
export const getExperimentResults = (projectId, experimentId) => api.get(`/projects/${projectId}/experiments/${experimentId}/results`)
export const createExperimentResult = (projectId, experimentId, data) => api.post(`/projects/${projectId}/experiments/${experimentId}/results`, data)

// Attribution
export const getAttributions = (projectId) => api.get(`/projects/${projectId}/attribution`)
export const createAttribution = (projectId, data, touchpoints) => api.post(`/projects/${projectId}/attribution`, data, { params: { touchpoints } })
export const getAttribution = (projectId, attributionId) => api.get(`/projects/${projectId}/attribution/${attributionId}`)

// Experiment Suggestions
export const getExperimentSuggestions = (projectId, params = {}) => api.get(`/projects/${projectId}/experiment-suggestions`, { params })
export const createExperimentSuggestion = (projectId, data) => api.post(`/projects/${projectId}/experiment-suggestions`, data)
export const dismissExperimentSuggestion = (projectId, suggestionId) => api.post(`/projects/${projectId}/experiment-suggestions/${suggestionId}/dismiss`)
export const autoGenerateSuggestions = (projectId) => api.post(`/projects/${projectId}/experiment-suggestions/auto-generate`)

// ==================== Phase 3.5: 策略复用与 Benchmark ====================

// Strategy Templates
export const getStrategyTemplates = (params = {}) => api.get('/strategies/templates', { params })
export const createStrategyTemplate = (data) => api.post('/strategies/templates', data)
export const getStrategyTemplate = (id) => api.get(`/strategies/templates/${id}`)
export const updateStrategyTemplate = (id, data) => api.patch(`/strategies/templates/${id}`, data)
export const deleteStrategyTemplate = (id) => api.delete(`/strategies/templates/${id}`)

// Strategy Instances
export const getStrategyInstances = (projectId, params = {}) => api.get(`/projects/${projectId}/strategies/instances`, { params })
export const createStrategyInstance = (projectId, data) => api.post(`/projects/${projectId}/strategies/instances`, data)
export const getStrategyInstance = (projectId, instanceId) => api.get(`/projects/${projectId}/strategies/instances/${instanceId}`)
export const updateStrategyInstanceStatus = (projectId, instanceId, status) => api.patch(`/projects/${projectId}/strategies/instances/${instanceId}`, null, { params: { status } })

// Benchmarks
export const analyzeBenchmark = (data, projectMetrics) => api.post('/benchmarks/analysis', projectMetrics, { params: data })
export const getProjectBenchmark = (projectId) => api.get(`/projects/${projectId}/benchmark`)
export const getBenchmarkReports = (params = {}) => api.get('/benchmarks/reports', { params })

// Strategy Recommendations
export const generateStrategyRecommendations = (projectId, industry) => api.post(`/projects/${projectId}/strategy-recommendations`, null, { params: { industry } })
export const getStrategyRecommendations = (projectId) => api.get(`/projects/${projectId}/strategy-recommendations`)

// Industry Insights
export const getIndustryInsights = (params = {}) => api.get('/insights/industry', { params })

export default api
