import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/client', () => ({ default: mockClient }))

import {
  listBackups,
  triggerBackup,
  restore,
  listDrills,
  createDrill
} from '@/api/recovery'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('recovery API', () => {
  it('listBackups gets /recovery/backups with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listBackups({ page_number: 1, page_size: 20, type: 'DATABASE', status: 'COMPLETED' })

    expect(mockClient.get).toHaveBeenCalledWith('/recovery/backups', {
      params: { page: 1, page_size: 20, type: 'DATABASE', status: 'COMPLETED' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('triggerBackup posts to /recovery/backups', async () => {
    const backup = { backup_id: 'b1', type: 'DATABASE', file_path: '/b1', file_size: 1024, status: 'RUNNING', started_at: '' }
    mockClient.post.mockResolvedValue({ data: backup })
    const data = { type: 'DATABASE' as const }

    const result = await triggerBackup(data)

    expect(mockClient.post).toHaveBeenCalledWith('/recovery/backups', data)
    expect(result).toEqual(backup)
  })

  it('restore posts to /recovery/restore', async () => {
    const restoreRecord = { restore_id: 'r1', backup_id: 'b1', status: 'RUNNING', started_at: '' }
    mockClient.post.mockResolvedValue({ data: restoreRecord })

    const result = await restore({ backup_id: 'b1' })

    expect(mockClient.post).toHaveBeenCalledWith('/recovery/restore', { backup_id: 'b1' })
    expect(result).toEqual(restoreRecord)
  })

  it('listDrills gets /recovery/drills with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listDrills({ page_number: 1, page_size: 20 })

    expect(mockClient.get).toHaveBeenCalledWith('/recovery/drills', {
      params: { page: 1, page_size: 20 }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('createDrill posts to /recovery/drills', async () => {
    const drill = { drill_id: 'd1', type: 'BACKUP_VERIFY', result: 'PASSED', executed_at: '' }
    mockClient.post.mockResolvedValue({ data: drill })
    const data = { type: 'BACKUP_VERIFY' as const, notes: 'test' }

    const result = await createDrill(data)

    expect(mockClient.post).toHaveBeenCalledWith('/recovery/drills', data)
    expect(result).toEqual(drill)
  })
})
