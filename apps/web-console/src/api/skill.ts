// Function: Skill / capability management API functions
// Time: 2026-07-05
// Author: AxeXie
import client from './client'
import type {
  Skill,
  SkillVersion,
  CreateSkillRequest,
  UpdateSkillRequest,
  SkillListParams,
  PageResult
} from '@/types/api'

/** GET /skills — paginated skill list */
export async function listSkills(params: SkillListParams): Promise<PageResult<Skill>> {
  const response = await client.get<PageResult<Skill>>('/skills', {
    params: {
      page: params.page_number,
      page_size: params.page_size,
      module: params.module,
      status: params.status
    }
  })
  return response.data
}

/** GET /skills/{id} — fetch a single skill */
export async function getSkill(id: string): Promise<Skill> {
  const response = await client.get<Skill>(`/skills/${id}`)
  return response.data
}

/** POST /skills — register a new skill */
export async function createSkill(data: CreateSkillRequest): Promise<Skill> {
  const response = await client.post<Skill>('/skills', data)
  return response.data
}

/** PUT /skills/{id} — update a skill */
export async function updateSkill(id: string, data: UpdateSkillRequest): Promise<Skill> {
  const response = await client.put<Skill>(`/skills/${id}`, data)
  return response.data
}

/** POST /skills/{id}/publish — publish a skill (generates a new version) */
export async function publishSkill(id: string): Promise<Skill> {
  const response = await client.post<Skill>(`/skills/${id}/publish`)
  return response.data
}

/** POST /skills/{id}/disable — disable a skill */
export async function disableSkill(id: string): Promise<Skill> {
  const response = await client.post<Skill>(`/skills/${id}/disable`)
  return response.data
}

/** POST /skills/{id}/enable — enable a skill */
export async function enableSkill(id: string): Promise<Skill> {
  const response = await client.post<Skill>(`/skills/${id}/enable`)
  return response.data
}

/** GET /skills/{id}/versions — fetch skill version history */
export async function getSkillVersions(id: string): Promise<SkillVersion[]> {
  const response = await client.get<SkillVersion[]>(`/skills/${id}/versions`)
  return response.data
}
