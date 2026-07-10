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
  listAlarms,
  listRules,
  createRule,
  updateRule,
  acknowledgeAlarm,
  resolveAlarm,
  listChannels,
  createChannel
} from '@/api/alarm'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('alarm API', () => {
  it('listAlarms gets /alarms with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listAlarms({ page_number: 1, page_size: 20, status: 'ACTIVE', severity: 'HIGH', source: 'robot' })

    expect(mockClient.get).toHaveBeenCalledWith('/alarms', {
      params: { page: 1, page_size: 20, status: 'ACTIVE', severity: 'HIGH', source: 'robot' }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('listRules gets /alarms/rules with remapped params', async () => {
    const raw = { items: [], total: 0, page: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: raw })

    const result = await listRules({ page_number: 1, page_size: 20, source: 'robot', enabled: true })

    expect(mockClient.get).toHaveBeenCalledWith('/alarms/rules', {
      params: { page: 1, page_size: 20, source: 'robot', enabled: true }
    })
    expect(result).toEqual({ items: [], total: 0, page_number: 1, page_size: 20 })
  })

  it('createRule posts to /alarms/rules', async () => {
    const rule = { rule_id: 'r1', name: 'Rule1', source: 'robot', metric: 'cpu', condition: '>', threshold: 90, severity: 'HIGH', enabled: true }
    mockClient.post.mockResolvedValue({ data: rule })
    const data = { name: 'Rule1', source: 'robot', metric: 'cpu', condition: '>', threshold: 90, severity: 'HIGH' as const }

    const result = await createRule(data)

    expect(mockClient.post).toHaveBeenCalledWith('/alarms/rules', data)
    expect(result).toEqual(rule)
  })

  it('updateRule puts to /alarms/rules/{ruleId}', async () => {
    const rule = { rule_id: 'r1', name: 'Updated', source: 'robot', metric: 'cpu', condition: '>', threshold: 95, severity: 'HIGH', enabled: true }
    mockClient.put.mockResolvedValue({ data: rule })

    const result = await updateRule('r1', { threshold: 95 })

    expect(mockClient.put).toHaveBeenCalledWith('/alarms/rules/r1', { threshold: 95 })
    expect(result).toEqual(rule)
  })

  it('acknowledgeAlarm posts to /alarms/{alarmId}/acknowledge', async () => {
    const alarm = { alarm_id: 'a1', rule_id: 'r1', source: 'robot', severity: 'HIGH', message: '', status: 'ACKNOWLEDGED', triggered_at: '' }
    mockClient.post.mockResolvedValue({ data: alarm })

    const result = await acknowledgeAlarm('a1')

    expect(mockClient.post).toHaveBeenCalledWith('/alarms/a1/acknowledge')
    expect(result).toEqual(alarm)
  })

  it('resolveAlarm posts to /alarms/{alarmId}/resolve', async () => {
    const alarm = { alarm_id: 'a1', rule_id: 'r1', source: 'robot', severity: 'HIGH', message: '', status: 'RESOLVED', triggered_at: '' }
    mockClient.post.mockResolvedValue({ data: alarm })

    const result = await resolveAlarm('a1')

    expect(mockClient.post).toHaveBeenCalledWith('/alarms/a1/resolve')
    expect(result).toEqual(alarm)
  })

  it('listChannels gets /alarms/channels', async () => {
    const channels = [{ channel_id: 'c1', name: 'Email', type: 'email', enabled: true }]
    mockClient.get.mockResolvedValue({ data: channels })

    const result = await listChannels()

    expect(mockClient.get).toHaveBeenCalledWith('/alarms/channels')
    expect(result).toEqual(channels)
  })

  it('createChannel posts to /alarms/channels', async () => {
    const channel = { channel_id: 'c1', name: 'Email', type: 'email', enabled: true }
    mockClient.post.mockResolvedValue({ data: channel })
    const data = { name: 'Email', type: 'email' as const, enabled: true }

    const result = await createChannel(data)

    expect(mockClient.post).toHaveBeenCalledWith('/alarms/channels', data)
    expect(result).toEqual(channel)
  })
})
