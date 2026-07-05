// Function: Alarm event, rule and notification channel API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  AlarmEvent,
  AlarmRule,
  NotificationChannel,
  CreateAlarmRuleRequest,
  UpdateAlarmRuleRequest,
  CreateNotificationChannelRequest,
  AlarmListParams,
  AlarmRuleListParams,
  PageResult
} from '@/types/api'

/** GET /alarms — paginated alarm event list */
export async function listAlarms(params: AlarmListParams): Promise<PageResult<AlarmEvent>> {
  const response = await client.get<{ items: AlarmEvent[]; total: number; page: number; page_size: number }>(
    '/alarms',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        status: params.status,
        severity: params.severity,
        source: params.source
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** GET /alarms/rules — paginated alarm rule list */
export async function listRules(params: AlarmRuleListParams): Promise<PageResult<AlarmRule>> {
  const response = await client.get<{ items: AlarmRule[]; total: number; page: number; page_size: number }>(
    '/alarms/rules',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        source: params.source,
        enabled: params.enabled
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** POST /alarms/rules — create an alarm rule */
export async function createRule(data: CreateAlarmRuleRequest): Promise<AlarmRule> {
  const response = await client.post<AlarmRule>('/alarms/rules', data)
  return response.data
}

/** PUT /alarms/rules/{ruleId} — update an alarm rule */
export async function updateRule(ruleId: string, data: UpdateAlarmRuleRequest): Promise<AlarmRule> {
  const response = await client.put<AlarmRule>(`/alarms/rules/${ruleId}`, data)
  return response.data
}

/** POST /alarms/{alarmId}/acknowledge — acknowledge an alarm */
export async function acknowledgeAlarm(alarmId: string): Promise<AlarmEvent> {
  const response = await client.post<AlarmEvent>(`/alarms/${alarmId}/acknowledge`)
  return response.data
}

/** POST /alarms/{alarmId}/resolve — resolve an alarm */
export async function resolveAlarm(alarmId: string): Promise<AlarmEvent> {
  const response = await client.post<AlarmEvent>(`/alarms/${alarmId}/resolve`)
  return response.data
}

/** GET /alarms/channels — list notification channels */
export async function listChannels(): Promise<NotificationChannel[]> {
  const response = await client.get<NotificationChannel[]>('/alarms/channels')
  return response.data
}

/** POST /alarms/channels — create a notification channel */
export async function createChannel(data: CreateNotificationChannelRequest): Promise<NotificationChannel> {
  const response = await client.post<NotificationChannel>('/alarms/channels', data)
  return response.data
}
