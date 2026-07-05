// Function: Audit log query API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type { AuditLog, AuditListParams, PageResult } from '@/types/api'

/** GET /audits — paginated audit log list with filters */
export async function listAudits(params: AuditListParams): Promise<PageResult<AuditLog>> {
  const response = await client.get<PageResult<AuditLog>>('/audits', { params })
  return response.data
}
