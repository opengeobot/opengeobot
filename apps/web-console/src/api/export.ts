// Function: Async export task API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type { ExportTask, CreateExportRequest } from '@/types/api'

/** POST /exports — create an async export task */
export async function createExport(data: CreateExportRequest): Promise<ExportTask> {
  const response = await client.post<ExportTask>('/exports', data)
  return response.data
}

/** GET /exports/{id} — poll an export task status */
export async function getExport(id: string): Promise<ExportTask> {
  const response = await client.get<ExportTask>(`/exports/${id}`)
  return response.data
}

/** GET /exports/{id}/download — download the export file as a blob */
export async function downloadExport(id: string, filename: string): Promise<void> {
  const response = await client.get(`/exports/${id}/download`, {
    responseType: 'blob'
  })
  const blob = new Blob([response.data as BlobPart], {
    type: response.headers['content-type'] as string
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
