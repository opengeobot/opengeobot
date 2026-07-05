// Function: Operations dashboard, metrics, health, reports and capacity API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  OpsDashboard,
  MetricSnapshot,
  HealthCheck,
  ReportRecord,
  CapacityForecast,
  ReportType,
  MetricQueryParams
} from '@/types/api'

/** GET /ops/dashboard — operations dashboard overview */
export async function getDashboard(): Promise<OpsDashboard> {
  const response = await client.get<OpsDashboard>('/ops/dashboard')
  return response.data
}

/** GET /ops/metrics — query metric snapshots */
export async function queryMetrics(params: MetricQueryParams): Promise<MetricSnapshot[]> {
  const response = await client.get<MetricSnapshot[]>('/ops/metrics', { params })
  return response.data
}

/** GET /ops/health — component health check summary */
export async function getHealth(): Promise<HealthCheck[]> {
  const response = await client.get<HealthCheck[]>('/ops/health')
  return response.data
}

/** GET /ops/reports/{reportType} — generate an operations report */
export async function generateReport(reportType: ReportType): Promise<ReportRecord> {
  const response = await client.get<ReportRecord>(`/ops/reports/${reportType}`)
  return response.data
}

/** GET /ops/capacity — capacity forecast */
export async function getCapacity(): Promise<CapacityForecast[]> {
  const response = await client.get<CapacityForecast[]>('/ops/capacity')
  return response.data
}
