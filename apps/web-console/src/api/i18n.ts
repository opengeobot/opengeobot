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

/** GET /i18n - paginated i18n resource list */
export async function listI18nResources(params: I18nListParams): Promise<PageResult<I18nResource>> {
  const response = await client.get<PageResult<I18nResource>>('/i18n', { params })
  return response.data
}

/** POST /i18n - create an i18n resource */
export async function createI18nResource(data: CreateI18nRequest): Promise<I18nResource> {
  const response = await client.post<I18nResource>('/i18n', data)
  return response.data
}

/** PUT /i18n/{resourceKey} - update an i18n resource */
export async function updateI18nResource(resourceKey: string, locale: string, data: UpdateI18nRequest): Promise<I18nResource> {
  const response = await client.put<I18nResource>(`/i18n/${resourceKey}`, data, {
    params: { locale }
  })
  return response.data
}

/** DELETE /i18n/{resourceKey} - delete an i18n resource */
export async function deleteI18nResource(resourceKey: string, locale: string): Promise<void> {
  await client.delete(`/i18n/${resourceKey}`, {
    params: { locale }
  })
}

/** POST /i18n/batch - batch import */
export async function batchImportI18n(file: File): Promise<{ imported: number }> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await client.post<{ imported: number }>('/i18n/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}
