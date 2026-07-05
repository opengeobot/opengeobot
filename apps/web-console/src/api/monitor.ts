// Function: Real-time monitoring overview and takeover API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  MonitorOverview,
  RobotMonitor,
  MissionMonitor,
  TakeoverRequest
} from '@/types/api'

/** GET /monitor/overview — overview cards (totals and alerts) */
export async function getOverview(): Promise<MonitorOverview> {
  const response = await client.get<MonitorOverview>('/monitor/overview')
  return response.data
}

/** GET /monitor/robots/{robotId} — real-time state of a single robot */
export async function getRobotMonitor(robotId: string): Promise<RobotMonitor> {
  const response = await client.get<RobotMonitor>(`/monitor/robots/${robotId}`)
  return response.data
}

/** GET /monitor/missions/{missionId} — real-time state of a single mission */
export async function getMissionMonitor(missionId: string): Promise<MissionMonitor> {
  const response = await client.get<MissionMonitor>(`/monitor/missions/${missionId}`)
  return response.data
}

/** POST /monitor/robots/{robotId}/takeover — take over manual control of a robot */
export async function takeover(robotId: string, data: TakeoverRequest): Promise<RobotMonitor> {
  const response = await client.post<RobotMonitor>(`/monitor/robots/${robotId}/takeover`, data)
  return response.data
}
