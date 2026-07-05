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
export type StatusTagType = 'health' | 'task' | 'enable-disable'

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
