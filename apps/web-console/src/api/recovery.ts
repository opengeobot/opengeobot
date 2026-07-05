// Function: Backup, restore and disaster recovery drill API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  BackupRecord,
  RestoreRecord,
  DrillRecord,
  RestoreRequest,
  CreateDrillRequest,
  TriggerBackupRequest,
  BackupListParams,
  PageResult
} from '@/types/api'

/** GET /recovery/backups — paginated backup list */
export async function listBackups(params: BackupListParams): Promise<PageResult<BackupRecord>> {
  const response = await client.get<{ items: BackupRecord[]; total: number; page: number; page_size: number }>(
    '/recovery/backups',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        type: params.type,
        status: params.status
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /recovery/backups — trigger a manual backup */
export async function triggerBackup(data: TriggerBackupRequest): Promise<BackupRecord> {
  const response = await client.post<BackupRecord>('/recovery/backups', data)
  return response.data
}

/** POST /recovery/restore — restore from a backup */
export async function restore(data: RestoreRequest): Promise<RestoreRecord> {
  const response = await client.post<RestoreRecord>('/recovery/restore', data)
  return response.data
}

/** GET /recovery/drills — paginated drill record list */
export async function listDrills(params: BackupListParams): Promise<PageResult<DrillRecord>> {
  const response = await client.get<{ items: DrillRecord[]; total: number; page: number; page_size: number }>(
    '/recovery/drills',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /recovery/drills — create and run a disaster recovery drill */
export async function createDrill(data: CreateDrillRequest): Promise<DrillRecord> {
  const response = await client.post<DrillRecord>('/recovery/drills', data)
  return response.data
}
