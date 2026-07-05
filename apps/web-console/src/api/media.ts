// Function: Media library upload, list and download API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  MediaAsset,
  MediaListParams,
  PageResult
} from '@/types/api'

/** POST /media/upload — upload a media file (multipart/form-data) */
export async function uploadMedia(
  file: File,
  onProgress?: (percent: number) => void
): Promise<MediaAsset> {
  const form = new FormData()
  form.append('file', file)
  const response = await client.post<MediaAsset>('/media/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
  })
  return response.data
}

/** GET /media — paginated media list */
export async function listMedia(params: MediaListParams): Promise<PageResult<MediaAsset>> {
  const response = await client.get<PageResult<MediaAsset>>('/media', { params })
  return response.data
}

/** GET /media/{id} — fetch a single media asset */
export async function getMedia(id: string): Promise<MediaAsset> {
  const response = await client.get<MediaAsset>(`/media/${id}`)
  return response.data
}

/** GET /media/{id}/download — download a media file as a blob */
export async function downloadMedia(id: string): Promise<Blob> {
  const response = await client.get<Blob>(`/media/${id}/download`, {
    responseType: 'blob'
  })
  return response.data
}

/** DELETE /media/{id} — delete a media asset */
export async function deleteMedia(id: string): Promise<void> {
  await client.delete(`/media/${id}`)
}
