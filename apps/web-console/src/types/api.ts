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
  id: string
  username: string
  display_name: string
  email: string
  phone: string
  avatar: string
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
export type StatusTagType = 'health' | 'task' | 'enable-disable' | 'robot' | 'publish'

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
  type_id: string
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
