import { describe, it, expect } from 'vitest'
import type { Robot, RobotCapability, Skill } from '@/types/api'

/** Mirrors CapabilityCatalogView aggregation of skill + robot capability DTOs. */
function aggregateCatalog(skills: Skill[], robots: Robot[]): string[] {
  const labels: string[] = []
  for (const skill of skills) {
    labels.push(`${skill.name}:${skill.skill_id}`)
  }
  for (const robot of robots) {
    for (const cap of robot.capabilities) {
      labels.push(`${robot.robot_id}:${cap.capability_type}`)
    }
  }
  return labels
}

describe('capability catalog aggregation', () => {
  it('uses skill.name/skill_id and capability_type objects', () => {
    const skills: Skill[] = [
      {
        skill_id: 'sk-1',
        name: 'Navigate',
        module: 'nav',
        description: '',
        status: 'active',
        current_version: 1,
        created_at: ''
      }
    ]
    const caps: RobotCapability[] = [
      { capability_type: 'lidar', capability_value: 'true' },
      { capability_type: 'arm', capability_value: '6dof' }
    ]
    const robots: Robot[] = [
      {
        robot_id: 'rb-1',
        name: 'Bot',
        model_id: 'm1',
        serial_number: 'SN',
        org_id: 'o1',
        status: 'online',
        capabilities: caps,
        created_at: ''
      }
    ]

    expect(aggregateCatalog(skills, robots)).toEqual([
      'Navigate:sk-1',
      'rb-1:lidar',
      'rb-1:arm'
    ])
  })
})
