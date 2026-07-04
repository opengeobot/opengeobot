// Function: Common API types
// Time: 2026-07-03
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
