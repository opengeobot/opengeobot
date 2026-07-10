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
  getDashboard,
  queryMetrics,
  getHealth,
  generateReport,
  getCapacity
} from '@/api/ops'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ops API', () => {
  it('getDashboard gets /ops/dashboard', async () => {
    const dashboard = {
      system_health: { overall: 'HEALTHY' },
      robot_stats: { total: 10, online: 8, offline: 1, busy: 1, error: 0 },
      mission_stats: { total: 5, active: 2, completed: 2, failed: 1 },
      alarm_stats: { active: 1, acknowledged: 0, resolved: 0 }
    }
    mockClient.get.mockResolvedValue({ data: dashboard })

    const result = await getDashboard()

    expect(mockClient.get).toHaveBeenCalledWith('/ops/dashboard')
    expect(result).toEqual(dashboard)
  })

  it('queryMetrics gets /ops/metrics with params', async () => {
    const metrics = [
      { metric_name: 'cpu', value: 50, unit: '%', timestamp: '2026-01-01' }
    ]
    mockClient.get.mockResolvedValue({ data: metrics })
    const params = { metric_name: 'cpu', start: '2026-01-01', end: '2026-01-02', limit: 100 }

    const result = await queryMetrics(params)

    expect(mockClient.get).toHaveBeenCalledWith('/ops/metrics', { params })
    expect(result).toEqual(metrics)
  })

  it('getHealth gets /ops/health', async () => {
    const health = [
      { component: 'db', status: 'HEALTHY', latency_ms: 10, last_check_at: '2026-01-01' }
    ]
    mockClient.get.mockResolvedValue({ data: health })

    const result = await getHealth()

    expect(mockClient.get).toHaveBeenCalledWith('/ops/health')
    expect(result).toEqual(health)
  })

  it('generateReport gets /ops/reports/{reportType}', async () => {
    const report = { report_type: 'daily', period_start: '', period_end: '', generated_at: '' }
    mockClient.get.mockResolvedValue({ data: report })

    const result = await generateReport('daily')

    expect(mockClient.get).toHaveBeenCalledWith('/ops/reports/daily')
    expect(result).toEqual(report)
  })

  it('getCapacity gets /ops/capacity', async () => {
    const forecast = [
      { resource: 'db', current_usage: 50, projected_usage: 70, threshold: 80, alert: false }
    ]
    mockClient.get.mockResolvedValue({ data: forecast })

    const result = await getCapacity()

    expect(mockClient.get).toHaveBeenCalledWith('/ops/capacity')
    expect(result).toEqual(forecast)
  })
})
