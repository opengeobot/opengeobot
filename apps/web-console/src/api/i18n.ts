// Function: I18n resource management API functions
// Time: 2026-07-04
// Author: AxeXie
import client from './client'
import type {
  I18nResource,
  I18nListParams,
  CreateI18nRequest,
  UpdateI18nRequest,
  PageResult
} from '@/types/api'

/** GET /i18n/resources — paginated i18n resource list */
export async function listI18nResources(params: I18nListParams): Promise<PageResult<I18nResource>> {
  const response = await client.get<PageResult<I18nResource>>('/i18n/resources', { params })
  return response.data
}

/** POST /i18n/resources — create an i18n resource */
export async function createI18nResource(data: CreateI18nRequest): Promise<I18nResource> {
  const response = await client.post<I18nResource>('/i18n/resources', data)
  return response.data
}

/** PUT /i18n/resources/{id} — update an i18n resource */
export async function updateI18nResource(id: string, data: UpdateI18nRequest): Promise<I18nResource> {
  const response = await client.put<I18nResource>(`/i18n/resources/${id}`, data)
  return response.data
}

/** DELETE /i18n/resources/{id} — delete an i18n resource */
export async function deleteI18nResource(id: string): Promise<void> {
  await client.delete(`/i18n/resources/${id}`)
}

/** POST /i18n/resources/batch-import — batch import CSV file */
export async function batchImportI18n(file: File): Promise<{ imported: number }> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await client.post<{ imported: number }>('/i18n/resources/batch-import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}
