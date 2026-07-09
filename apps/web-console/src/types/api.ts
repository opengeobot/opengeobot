// Function: Common API types
// Time: 2026-07-04
// Author: AxeXie

export interface ProblemDetails {
  type: string
  title: string
  status: number
  code: string
  message_key: string
  arguments: Record<string, unknown>
  trace_id: string
  instance: string
}

export interface PageRequest {
  page_number: number
  page_size: number
  sort_by?: string
  sort_direction?: 'asc' | 'desc'
}

export interface PageResult<T> {
  items: T[]
  total: number
  page_number: number
  page_size: number
}

/** Auth token response from POST /auth/login and POST /auth/refresh */
export interface TokenResponse {
  access_token: string
  refresh_token: string
  expires_in: number
}

/** User profile from GET /profile */
export interface UserProfile {
  user_id: string
  username: string
  display_name: string
  email: string | null
  phone: string | null
  avatar: string | null
  status: string
  permissions: string[]
}

/** Update profile request body for PUT /profile */
export interface UpdateProfileRequest {
  display_name?: string
  email?: string
  phone?: string
}

/** Sort direction for table columns */
export type SortDirection = 'asc' | 'desc'

/** Column definition for DataTable */
export interface DataTableColumn {
  key: string
  title: string
  width?: string | number
  sortable?: boolean
}

/** Pagination state for DataTable */
export interface DataTablePagination {
  page_number: number
  page_size: number
  total: number
}

/** Sort state emitted by DataTable */
export interface SortState {
  key: string
  direction: SortDirection
}

/** Supported field types for FormBuilder */
export type FormFieldType =
  | 'text'
  | 'password'
  | 'email'
  | 'number'
  | 'select'
  | 'textarea'

/** Validation rule returning true when valid, or an error message string */
export type ValidationRule = (value: unknown) => true | string

/** Option for select-type fields */
export interface SelectOption {
  label: string
  value: string | number
}

/** Field definition for FormBuilder */
export interface FormField {
  key: string
  label: string
  type: FormFieldType
  required?: boolean
  rules?: ValidationRule[]
  options?: SelectOption[]
  placeholder?: string
}

/** Status tag visual category */
export type StatusTagType =
  | 'health'
  | 'task'
  | 'enable-disable'
  | 'robot'
  | 'publish'
  | 'alarm'
  | 'severity'
  | 'fleet'
  | 'ota'
  | 'recovery'
  | 'memory'

/** Generic filter params extending page request */
export interface FilterParams extends PageRequest {
  keyword?: string
  [key: string]: unknown
}

// ---- User management ----

export interface User {
  id: string
  username: string
  display_name: string
  email: string
  phone: string
  org_id: string
  org_name?: string
  status: string
  created_at: string
}

export interface CreateUserRequest {
  username: string
  display_name: string
  email: string
  phone: string
  password: string
  org_id: string
  role_ids: string[]
}

export interface UpdateUserRequest {
  display_name?: string
  email?: string
  phone?: string
  org_id?: string
}

export interface UserListParams extends FilterParams {
  keyword?: string
  org_id?: string
  status?: string
}

// ---- Organization ----

export interface Org {
  id: string
  org_code: string
  org_name: string
  parent_id: string | null
  description: string
  status: string
  children?: Org[]
}

export interface CreateOrgRequest {
  org_code: string
  org_name: string
  parent_id: string | null
  description: string
}

export interface UpdateOrgRequest {
  org_name?: string
  description?: string
  status?: string
}

// ---- Role ----

export interface Role {
  id: string
  role_name: string
  role_code: string
  description: string
  status: string
  built_in: boolean
}

export interface CreateRoleRequest {
  role_name: string
  role_code: string
  description: string
}

export interface UpdateRoleRequest {
  role_name?: string
  description?: string
  status?: string
}

// ---- Permission ----

export interface Permission {
  id: string
  permission_code: string
  permission_name: string
  module: string
  description: string
}

export interface PermissionGroup {
  module: string
  permissions: Permission[]
}

// ---- Dictionary ----

export interface DictType {
  id: string
  type_code: string
  type_name: string
  status: string
  version: number
}

export interface DictItem {
  id: string
  type_id: string
  item_code: string
  item_value: string
  label_zh_cn: string
  label_en_us: string
  sort_order: number
  status: string
}

export interface CreateDictTypeRequest {
  type_code: string
  type_name: string
}

export interface UpdateDictTypeRequest {
  type_name?: string
  status?: string
}

export interface CreateDictItemRequest {
  item_code: string
  item_value: string
  label_zh_cn: string
  label_en_us: string
  sort_order: number
}

export interface UpdateDictItemRequest {
  item_value?: string
  label_zh_cn?: string
  label_en_us?: string
  sort_order?: number
  status?: string
}

// ---- I18n ----

export interface I18nResource {
  id: string
  resource_key: string
  locale: string
  resource_value: string
  module: string
}

export interface I18nListParams extends FilterParams {
  locale?: string
  module?: string
}

export interface CreateI18nRequest {
  resource_key: string
  locale: string
  resource_value: string
  module: string
}

export interface UpdateI18nRequest {
  resource_value?: string
  module?: string
}

// ---- Config ----

export interface Config {
  id: string
  config_key: string
  config_value: string
  value_type: string
  module: string
  description: string
  version: number
}

export interface ConfigHistory {
  id: string
  config_id: string
  config_value: string
  value_type: string
  version: number
  updated_by: string
  updated_at: string
}

export interface CreateConfigRequest {
  config_key: string
  config_value: string
  value_type: string
  module: string
  description: string
}

export interface UpdateConfigRequest {
  config_value?: string
  value_type?: string
  module?: string
  description?: string
}

// ---- Audit ----

export interface AuditLog {
  id: string
  occurred_at: string
  actor_id: string
  actor: string
  action: string
  resource_type: string
  resource_id: string
  result: string
  trace_id: string
  payload_before: string | null
  payload_after: string | null
}

export interface AuditListParams extends FilterParams {
  actor_id?: string
  action?: string
  resource_type?: string
  trace_id?: string
  start_time?: string
  end_time?: string
}

// ---- Robot ----

export interface Robot {
  id: string
  name: string
  model_id: string
  model_name?: string
  serial_number: string
  org_id: string
  org_name?: string
  status: string
  last_seen: string | null
  capabilities: string[]
  created_at: string
}

export interface RobotModel {
  id: string
  model_code: string
  model_name: string
  vendor: string
  description: string
}

export interface RobotGroup {
  id: string
  group_code: string
  group_name: string
  description: string
}

export interface CreateRobotRequest {
  name: string
  model_id: string
  serial_number: string
  org_id: string
  capabilities: string[]
}

export interface UpdateRobotRequest {
  name?: string
  model_id?: string
  serial_number?: string
  org_id?: string
}

export interface RobotListParams extends FilterParams {
  name?: string
  status?: string
  org_id?: string
  model_id?: string
}

// ---- Skill / Capability ----

export interface Skill {
  id: string
  skill_code: string
  skill_name: string
  module: string
  description: string
  status: string
  current_version: string
  created_at: string
}

export interface SkillVersion {
  id: string
  skill_id: string
  version: string
  change_log: string
  status: string
  published_by: string
  published_at: string
}

export interface CreateSkillRequest {
  skill_code: string
  skill_name: string
  module: string
  description: string
}

export interface UpdateSkillRequest {
  skill_name?: string
  module?: string
  description?: string
}

export interface SkillListParams extends FilterParams {
  module?: string
  status?: string
}

// ---- MCP tool ----

export interface McpTool {
  id: string
  tool_code: string
  tool_name: string
  description: string
  input_schema: Record<string, unknown>
  status: string
  registered_at: string
}

export interface McpInvocation {
  id: string
  tool_id: string
  tool_name: string
  trace_id: string
  caller: string
  status: string
  input: string | null
  output: string | null
  error: string | null
  started_at: string
  finished_at: string | null
}

export interface RegisterMcpToolRequest {
  tool_code: string
  tool_name: string
  description: string
  input_schema: Record<string, unknown>
}

export interface InvokeMcpToolRequest {
  input: Record<string, unknown>
  trace_id?: string
}

export interface McpListParams extends FilterParams {
  status?: string
}

// ---- Mission ----

export interface MissionStep {
  id: string
  step_index: number
  action: string
  target: string
  parameters: Record<string, unknown>
  status: string
}

export interface Mission {
  id: string
  name: string
  description: string
  robot_id: string
  robot_name?: string
  status: string
  priority: number
  steps: MissionStep[]
  created_at: string
  updated_at: string
}

export interface MissionTemplate {
  id: string
  template_code: string
  template_name: string
  description: string
  steps: MissionStep[]
}

export interface CreateMissionRequest {
  name: string
  description: string
  robot_id: string
  priority: number
  steps: Array<{
    action: string
    target: string
    parameters: Record<string, unknown>
  }>
}

export interface UpdateMissionRequest {
  name?: string
  description?: string
  robot_id?: string
  priority?: number
}

export interface RevisePlanRequest {
  steps: Array<{
    action: string
    target: string
    parameters: Record<string, unknown>
  }>
}

export interface CreateMissionTemplateRequest {
  template_code: string
  template_name: string
  description: string
  steps: Array<{
    action: string
    target: string
    parameters: Record<string, unknown>
  }>
}

export interface RejectMissionRequest {
  reason: string
}

export interface MissionListParams extends FilterParams {
  robot_id?: string
  status?: string
  priority?: number
}

// ---- Policy ----

export interface Policy {
  id: string
  policy_code: string
  policy_name: string
  description: string
  status: string
  version: number
  scope: string
  rules: Record<string, unknown>
  created_at: string
}

export interface PolicyVersion {
  id: string
  policy_id: string
  version: number
  rules: Record<string, unknown>
  status: string
  published_by: string
  published_at: string
}

export interface CreatePolicyRequest {
  policy_code: string
  policy_name: string
  description: string
  scope: string
  rules: Record<string, unknown>
}

export interface UpdatePolicyRequest {
  policy_name?: string
  description?: string
  scope?: string
  rules?: Record<string, unknown>
}

export interface PolicyListParams extends FilterParams {
  status?: string
  scope?: string
}

// ---- Safety ----

export interface SafetyState {
  robot_id: string | null
  e_stopped: boolean
  locked: boolean
  reason: string
  last_event_id: string | null
  updated_at: string
}

export interface SafetyEvent {
  id: string
  occurred_at: string
  robot_id: string
  event_type: string
  level: string
  source: string
  description: string
  trace_id: string
  resolved: boolean
}

export interface EmergencyStopRequest {
  robot_id?: string
  reason?: string
}

export interface ResetSafetyRequest {
  robot_id?: string
}

export interface SafetyEventListParams extends FilterParams {
  robot_id?: string
  event_type?: string
  level?: string
  start_time?: string
  end_time?: string
}

// ---- Map ----

export interface GameMap {
  id: string
  map_code: string
  map_name: string
  status: string
  version: number
  frame: string
  description: string
  created_at: string
}

export interface MapArea {
  id: string
  map_id: string
  area_code: string
  area_name: string
  area_type: string
  polygon: Record<string, unknown>
}

export interface RestrictedArea {
  id: string
  map_id: string
  area_code: string
  area_name: string
  level: string
  polygon: Record<string, unknown>
}

export interface CreateMapRequest {
  map_code: string
  map_name: string
  frame: string
  description: string
}

export interface UpdateMapRequest {
  map_name?: string
  frame?: string
  description?: string
}

export interface CreateAreaRequest {
  area_code: string
  area_name: string
  area_type: string
  polygon: Record<string, unknown>
}

export interface CreateRestrictedAreaRequest {
  area_code: string
  area_name: string
  level: string
  polygon: Record<string, unknown>
}

export interface MapListParams extends FilterParams {
  status?: string
}

// ---- Monitor ----

export interface MonitorOverview {
  total_robots: number
  online_robots: number
  busy_robots: number
  active_missions: number
  alerts: number
}

export interface RobotMonitor {
  robot_id: string
  robot_name: string
  status: string
  battery: number
  position: { x: number; y: number; yaw: number }
  current_mission_id: string | null
  last_seen: string
}

export interface MissionMonitor {
  mission_id: string
  mission_name: string
  robot_name: string
  status: string
  progress: number
  current_step: number
  total_steps: number
  trace_id: string
}

export interface TakeoverRequest {
  operator_id: string
  reason?: string
}

// ---- Media ----

export interface MediaAsset {
  id: string
  file_name: string
  mime_type: string
  size: number
  url: string
  thumbnail_url: string | null
  uploaded_by: string
  created_at: string
}

export interface MediaListParams extends FilterParams {
  mime_type?: string
}

// ---- Trace ----

export interface Trace {
  id: string
  trace_id: string
  root_trace_id: string
  operation: string
  resource_type: string
  resource_id: string
  actor_id: string
  status: string
  started_at: string
  finished_at: string | null
  duration_ms: number | null
}

export interface TraceSpan {
  id: string
  trace_id: string
  parent_id: string | null
  operation: string
  service: string
  status: string
  started_at: string
  duration_ms: number
  attributes: Record<string, unknown>
}

export interface TraceReplay {
  trace_id: string
  spans: TraceSpan[]
  events: Array<{
    id: string
    occurred_at: string
    type: string
    payload: Record<string, unknown>
  }>
}

export interface TraceListParams extends FilterParams {
  resource_type?: string
  resource_id?: string
  status?: string
  start_time?: string
  end_time?: string
}

// ---- Export ----

export interface ExportTask {
  id: string
  status: string
  file_url: string | null
  created_at: string
}

export interface CreateExportRequest {
  resource_type: string
  filters: Record<string, unknown>
}

// ---- Fleet scheduling ----

export type FleetScheduleStatus = 'PENDING' | 'APPROVED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
export type FleetSchedulePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type ConflictType = 'TIME_OVERLAP' | 'ROBOT_BUSY' | 'PATH_COLLISION' | 'RESOURCE_CONTENTION'
export type ConflictResolution = 'REORDER' | 'REASSIGN' | 'CANCEL'
export type ConflictStatus = 'OPEN' | 'RESOLVED'
export type FailoverStatus = 'INITIATED' | 'COMPLETED' | 'FAILED'

export interface FleetSchedule {
  schedule_id: string
  mission_id: string
  robot_id: string
  planned_start: string
  planned_end: string
  priority: FleetSchedulePriority
  status: FleetScheduleStatus
  created_at: string
}

export interface ConflictRecord {
  conflict_id: string
  schedule_ids: string[]
  conflict_type: ConflictType
  description?: string
  detected_at: string
  resolved_at?: string
  resolution?: ConflictResolution
  status: ConflictStatus
}

export interface FailoverEvent {
  failover_id: string
  robot_id: string
  mission_id: string
  reason: string
  target_robot_id?: string
  status: FailoverStatus
  occurred_at: string
}

export interface CreateScheduleRequest {
  mission_id: string
  robot_id: string
  planned_start: string
  planned_end: string
  priority?: FleetSchedulePriority
}

export interface ResolveConflictRequest {
  resolution: ConflictResolution
  target_robot_id?: string
}

export interface TriggerFailoverRequest {
  robot_id: string
  mission_id: string
  reason: string
  target_robot_id?: string
}

export interface FleetScheduleListParams extends FilterParams {
  status?: FleetScheduleStatus
  robot_id?: string
  mission_id?: string
}

export interface ConflictListParams extends FilterParams {
  status?: ConflictStatus
}

export interface FailoverListParams extends FilterParams {
  robot_id?: string
  status?: FailoverStatus
}

// ---- Alarm ----

export type AlarmSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type AlarmStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED'
export type AlarmChannelType = 'in-app' | 'webhook' | 'email'

export interface AlarmEvent {
  alarm_id: string
  rule_id: string
  source: string
  severity: AlarmSeverity
  message: string
  status: AlarmStatus
  triggered_at: string
  acknowledged_by?: string
  acknowledged_at?: string
  resolved_at?: string
  trace_id?: string
}

export interface AlarmRule {
  rule_id: string
  name: string
  source: string
  metric: string
  condition: string
  threshold: number
  severity: AlarmSeverity
  enabled: boolean
  created_at?: string
  updated_at?: string
  created_by?: string
}

export interface NotificationChannel {
  channel_id: string
  name: string
  type: AlarmChannelType
  config?: Record<string, unknown>
  enabled: boolean
  created_at?: string
}

export interface CreateAlarmRuleRequest {
  name: string
  source: string
  metric: string
  condition: string
  threshold: number
  severity: AlarmSeverity
  enabled?: boolean
}

export interface UpdateAlarmRuleRequest {
  name?: string
  condition?: string
  threshold?: number
  severity?: AlarmSeverity
  enabled?: boolean
}

export interface CreateNotificationChannelRequest {
  name: string
  type: AlarmChannelType
  config?: Record<string, unknown>
  enabled?: boolean
}

export interface AlarmListParams extends FilterParams {
  status?: AlarmStatus
  severity?: AlarmSeverity
  source?: string
}

export interface AlarmRuleListParams extends FilterParams {
  source?: string
  enabled?: boolean
}

// ---- Ops dashboard ----

export type HealthState = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY'
export type ReportType = 'daily' | 'weekly' | 'monthly'

export interface HealthCheck {
  component: string
  status: HealthState
  latency_ms?: number
  error_message?: string
  last_check_at: string
}

export interface OpsSystemHealth {
  overall: HealthState
  components?: HealthCheck[]
}

export interface RobotStats {
  total: number
  online: number
  offline: number
  busy: number
  error: number
}

export interface MissionStats {
  total: number
  active: number
  completed: number
  failed: number
}

export interface AlarmStats {
  active: number
  acknowledged: number
  resolved: number
}

export interface OpsDashboard {
  system_health: OpsSystemHealth
  robot_stats: RobotStats
  mission_stats: MissionStats
  alarm_stats: AlarmStats
}

export interface MetricSnapshot {
  metric_name: string
  value: number
  unit?: string
  tags?: Record<string, unknown>
  timestamp: string
}

export interface ReportRecord {
  report_type: ReportType
  period_start: string
  period_end: string
  summary?: Record<string, unknown>
  generated_at: string
}

export interface CapacityForecast {
  resource: string
  current_usage: number
  projected_usage: number
  threshold: number
  unit?: string
  alert: boolean
}

export interface MetricQueryParams {
  metric_name?: string
  start?: string
  end?: string
  limit?: number
}

// ---- OTA ----

export type PackageType = 'FIRMWARE' | 'SKILL_BUNDLE'
export type CampaignStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'ROLLED_BACK' | 'FAILED'
export type DeploymentStatus = 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'FAILED' | 'ROLLED_BACK'

export interface FirmwarePackage {
  package_id: string
  name: string
  version: string
  type: PackageType
  file_path: string
  file_size: number
  checksum: string
  description?: string
  created_by?: string
  created_at: string
}

export interface ReleaseCampaign {
  campaign_id: string
  package_id: string
  canary_percent: number
  status: CampaignStatus
  target_robots: string[]
  started_at?: string
  completed_at?: string
  created_by?: string
  created_at: string
}

export interface DeploymentRecord {
  record_id: string
  campaign_id: string
  robot_id: string
  status: DeploymentStatus
  started_at: string
  completed_at?: string
  error?: string
}

export interface CampaignDetail {
  campaign: ReleaseCampaign
  deployments: DeploymentRecord[]
}

export interface CreateCampaignRequest {
  package_id: string
  target_robots: string[]
  canary_percent: number
}

export interface UploadPackageRequest {
  name: string
  version: string
  type: PackageType
  description?: string
}

export interface PackageListParams extends FilterParams {
  type?: PackageType
}

export interface CampaignListParams extends FilterParams {
  status?: CampaignStatus
}

// ---- Backup and recovery ----

export type BackupType = 'DATABASE' | 'MINIO'
export type BackupStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'
export type DrillType = 'BACKUP_VERIFY' | 'RESTORE_SIMULATION' | 'FAILOVER'
export type DrillResult = 'PASSED' | 'FAILED' | 'PARTIAL'

export interface BackupRecord {
  backup_id: string
  type: BackupType
  file_path: string
  file_size: number
  status: BackupStatus
  started_at: string
  completed_at?: string
  error_message?: string
  created_by?: string
}

export interface RestoreRecord {
  restore_id: string
  backup_id: string
  status: BackupStatus
  started_at: string
  completed_at?: string
  error_message?: string
  restored_by?: string
}

export interface DrillRecord {
  drill_id: string
  type: DrillType
  result: DrillResult
  notes?: string
  executed_at: string
  executed_by?: string
}

export interface RestoreRequest {
  backup_id: string
}

export interface CreateDrillRequest {
  type: DrillType
  notes?: string
}

export interface TriggerBackupRequest {
  type: BackupType
}

export interface BackupListParams extends FilterParams {
  type?: BackupType
  status?: BackupStatus
}

// ---- Task memory ----

export type CaseResult = 'SUCCESS' | 'FAILURE'
export type FailureType = 'TIMEOUT' | 'SKILL_ERROR' | 'SAFETY_VIOLATION' | 'HARDWARE_FAULT' | 'UNKNOWN'
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'APPLIED'

export interface TaskCase {
  case_id: string
  mission_id: string
  robot_id: string
  skill_id: string
  result: CaseResult
  duration_ms?: number
  context?: Record<string, unknown>
  error_message?: string
  occurred_at: string
  trace_id?: string
}

export interface FailureCase {
  case_id: string
  failure_type: FailureType
  root_cause: string
  environment?: Record<string, unknown>
  similar_cases?: string[]
}

export interface TaskCaseDetail {
  task_case: TaskCase
  failure_case?: FailureCase
}

export interface ImprovementSuggestion {
  suggestion_id: string
  case_id: string
  suggestion_text: string
  confidence: number
  status: SuggestionStatus
  feedback?: string
  created_at: string
}

export interface FeedbackRequest {
  suggestion_id: string
  feedback: string
}

export interface TaskCaseListParams extends FilterParams {
  result?: CaseResult
  robot_id?: string
  skill_id?: string
}

export interface SuggestionListParams extends FilterParams {
  status?: SuggestionStatus
}
