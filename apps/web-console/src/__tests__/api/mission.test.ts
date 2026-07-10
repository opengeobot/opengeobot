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
  listMissions,
  getMission,
  createMission,
  updateMission,
  revisePlan,
  startMission,
  pauseMission,
  resumeMission,
  cancelMission,
  listMissionTemplates,
  createMissionTemplate,
  submitApproval,
  approveMission,
  rejectMission
} from '@/api/mission'

beforeEach(() => {
  vi.clearAllMocks()
})

const mockMission = {
  id: 'm1', name: 'Mission1', description: '', robot_id: 'r1', status: 'pending',
  priority: 0, steps: [], created_at: '', updated_at: ''
}

describe('mission API', () => {
  it('listMissions gets /missions with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listMissions({ page_number: 1, page_size: 20, robot_id: 'r1' })

    expect(mockClient.get).toHaveBeenCalledWith('/missions', { params: { page_number: 1, page_size: 20, robot_id: 'r1' } })
    expect(result).toEqual(page)
  })

  it('getMission gets /missions/{id}', async () => {
    mockClient.get.mockResolvedValue({ data: mockMission })

    const result = await getMission('m1')

    expect(mockClient.get).toHaveBeenCalledWith('/missions/m1')
    expect(result).toEqual(mockMission)
  })

  it('createMission posts to /missions', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })
    const data = { name: 'Mission1', description: '', robot_id: 'r1', priority: 0, steps: [] }

    const result = await createMission(data)

    expect(mockClient.post).toHaveBeenCalledWith('/missions', data)
    expect(result).toEqual(mockMission)
  })

  it('updateMission puts to /missions/{id}', async () => {
    mockClient.put.mockResolvedValue({ data: mockMission })

    const result = await updateMission('m1', { name: 'Updated' })

    expect(mockClient.put).toHaveBeenCalledWith('/missions/m1', { name: 'Updated' })
    expect(result).toEqual(mockMission)
  })

  it('revisePlan posts to /missions/{id}/revise', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })
    const data = { steps: [{ action: 'move', target: 'loc1', parameters: {} }] }

    const result = await revisePlan('m1', data)

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/revise', data)
    expect(result).toEqual(mockMission)
  })

  it('startMission posts to /missions/{id}/start', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })

    await startMission('m1')

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/start')
  })

  it('pauseMission posts to /missions/{id}/pause', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })

    await pauseMission('m1')

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/pause')
  })

  it('resumeMission posts to /missions/{id}/resume', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })

    await resumeMission('m1')

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/resume')
  })

  it('cancelMission posts to /missions/{id}/cancel', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })

    await cancelMission('m1')

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/cancel')
  })

  it('listMissionTemplates gets /mission-templates with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })

    const result = await listMissionTemplates({ page_number: 1, page_size: 20 })

    expect(mockClient.get).toHaveBeenCalledWith('/mission-templates', { params: { page_number: 1, page_size: 20 } })
    expect(result).toEqual(page)
  })

  it('createMissionTemplate posts to /mission-templates', async () => {
    const template = { id: 't1', template_code: 'T1', template_name: 'Temp', description: '', steps: [] }
    mockClient.post.mockResolvedValue({ data: template })
    const data = { template_code: 'T1', template_name: 'Temp', description: '', steps: [] }

    const result = await createMissionTemplate(data)

    expect(mockClient.post).toHaveBeenCalledWith('/mission-templates', data)
    expect(result).toEqual(template)
  })

  it('submitApproval posts to /missions/{id}/submit-approval', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })

    await submitApproval('m1')

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/submit-approval')
  })

  it('approveMission posts to /missions/{id}/approve', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })

    await approveMission('m1')

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/approve')
  })

  it('rejectMission posts to /missions/{id}/reject with reason', async () => {
    mockClient.post.mockResolvedValue({ data: mockMission })

    await rejectMission('m1', { reason: 'invalid plan' })

    expect(mockClient.post).toHaveBeenCalledWith('/missions/m1/reject', { reason: 'invalid plan' })
  })
})
