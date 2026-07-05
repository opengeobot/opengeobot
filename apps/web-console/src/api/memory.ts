// Function: Task memory case, failure analysis and improvement suggestion API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  TaskCase,
  TaskCaseDetail,
  ImprovementSuggestion,
  FeedbackRequest,
  TaskCaseListParams,
  SuggestionListParams,
  PageResult
} from '@/types/api'

/** GET /memory/cases — paginated task case list */
export async function listCases(params: TaskCaseListParams): Promise<PageResult<TaskCase>> {
  const response = await client.get<{ items: TaskCase[]; total: number; page: number; page_size: number }>(
    '/memory/cases',
    {
      params: {
        page: params.page_number,
        page_size: params.page_size,
        result: params.result,
        robot_id: params.robot_id,
        skill_id: params.skill_id
      }
    }
  )
  const d = response.data
  return { items: d.items, total: d.total, page_number: d.page, page_size: d.page_size }
}

/** GET /memory/cases/{caseId} — fetch a task case with optional failure analysis */
export async function getCase(caseId: string): Promise<TaskCaseDetail> {
  const response = await client.get<TaskCaseDetail>(`/memory/cases/${caseId}`)
  return response.data
}

/** GET /memory/suggestions — paginated improvement suggestion list */
export async function listSuggestions(params: SuggestionListParams): Promise<PageResult<ImprovementSuggestion>> {
  const response = await client.get<{ items: ImprovementSuggestion[]; total: number; page: number; page_size: number }>(
    '/memory/suggestions',
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

/** POST /memory/feedback — submit feedback on an improvement suggestion */
export async function submitFeedback(data: FeedbackRequest): Promise<ImprovementSuggestion> {
  const response = await client.post<ImprovementSuggestion>('/memory/feedback', data)
  return response.data
}
