// Function: OTA package upload and release campaign API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  FirmwarePackage,
  ReleaseCampaign,
  CampaignDetail,
  CreateCampaignRequest,
  UploadPackageRequest,
  PackageListParams,
  CampaignListParams,
  PageResult
} from '@/types/api'

/** GET /ota/packages — paginated firmware/skill package list */
export async function listPackages(params: PackageListParams): Promise<PageResult<FirmwarePackage>> {
  const response = await client.get<{ items: FirmwarePackage[]; total: number; page: number; page_size: number }>(
    '/ota/packages',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        type: params.type
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /ota/packages — upload an OTA package (multipart/form-data) */
export async function uploadPackage(
  file: File,
  meta: UploadPackageRequest,
  onProgress?: (percent: number) => void
): Promise<FirmwarePackage> {
  const form = new FormData()
  form.append('file', file)
  form.append('name', meta.name)
  form.append('version', meta.version)
  form.append('type', meta.type)
  if (meta.description) {
    form.append('description', meta.description)
  }
  const response = await client.post<FirmwarePackage>('/ota/packages', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
  })
  return response.data
}

/** GET /ota/campaigns — paginated release campaign list */
export async function listCampaigns(params: CampaignListParams): Promise<PageResult<ReleaseCampaign>> {
  const response = await client.get<{ items: ReleaseCampaign[]; total: number; page: number; page_size: number }>(
    '/ota/campaigns',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        status: params.status
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /ota/campaigns — create a release campaign */
export async function createCampaign(data: CreateCampaignRequest): Promise<ReleaseCampaign> {
  const response = await client.post<ReleaseCampaign>('/ota/campaigns', data)
  return response.data
}

/** GET /ota/campaigns/{campaignId} — fetch campaign with deployment records */
export async function getCampaign(campaignId: string): Promise<CampaignDetail> {
  const response = await client.get<CampaignDetail>(`/ota/campaigns/${campaignId}`)
  return response.data
}

/** POST /ota/campaigns/{campaignId}/rollback — roll back a campaign deployment */
export async function rollback(campaignId: string): Promise<ReleaseCampaign> {
  const response = await client.post<ReleaseCampaign>(`/ota/campaigns/${campaignId}/rollback`)
  return response.data
}
