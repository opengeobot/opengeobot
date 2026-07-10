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
  listEdgeGateways,
  getEdgeGateway,
  registerEdgeGateway,
  activateEdgeGateway,
  revokeEdgeGateway,
  heartbeatEdgeGateway,
  rotateCertificate
} from '@/api/edge'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('edge API', () => {
  it('listEdgeGateways gets /edge-gateways with params', async () => {
    const page = { items: [], total: 0, page_number: 1, page_size: 20 }
    mockClient.get.mockResolvedValue({ data: page })
    const params = { page_number: 1, page_size: 20, status: 'ACTIVE' }

    const result = await listEdgeGateways(params)

    expect(mockClient.get).toHaveBeenCalledWith('/edge-gateways', { params })
    expect(result).toEqual(page)
  })

  it('getEdgeGateway gets /edge-gateways/{id}', async () => {
    const gateway = { gateway_id: 'g1', name: 'gw', org_id: 'o1', status: 'ACTIVE' }
    mockClient.get.mockResolvedValue({ data: gateway })

    const result = await getEdgeGateway('g1')

    expect(mockClient.get).toHaveBeenCalledWith('/edge-gateways/g1')
    expect(result).toEqual(gateway)
  })

  it('registerEdgeGateway posts to /edge-gateways', async () => {
    const gateway = { gateway_id: 'g1', name: 'gw', org_id: 'o1', status: 'PENDING' }
    mockClient.post.mockResolvedValue({ data: gateway })
    const data = { name: 'gw', org_id: 'o1' }

    const result = await registerEdgeGateway(data)

    expect(mockClient.post).toHaveBeenCalledWith('/edge-gateways', data)
    expect(result).toEqual(gateway)
  })

  it('activateEdgeGateway posts activate', async () => {
    mockClient.post.mockResolvedValue({ data: { gateway_id: 'g1', status: 'ACTIVE' } })

    await activateEdgeGateway('g1', { reason: 'ok' })

    expect(mockClient.post).toHaveBeenCalledWith('/edge-gateways/g1/activate', { reason: 'ok' })
  })

  it('revokeEdgeGateway posts revoke', async () => {
    mockClient.post.mockResolvedValue({ data: { gateway_id: 'g1', status: 'REVOKED' } })

    await revokeEdgeGateway('g1')

    expect(mockClient.post).toHaveBeenCalledWith('/edge-gateways/g1/revoke', {})
  })

  it('heartbeatEdgeGateway posts heartbeat', async () => {
    mockClient.post.mockResolvedValue({ data: { gateway_id: 'g1' } })

    await heartbeatEdgeGateway('g1', { runtime_version: '1.0' })

    expect(mockClient.post).toHaveBeenCalledWith('/edge-gateways/g1/heartbeat', { runtime_version: '1.0' })
  })

  it('rotateCertificate posts certificate-rotations', async () => {
    const cert = { cert_id: 'c1', gateway_id: 'g1', fingerprint: 'fp', expires_at: '2027-01-01T00:00:00Z', status: 'ACTIVE', created_at: '' }
    mockClient.post.mockResolvedValue({ data: cert })
    const data = { fingerprint: 'fp', expires_at: '2027-01-01T00:00:00Z' }

    const result = await rotateCertificate('g1', data)

    expect(mockClient.post).toHaveBeenCalledWith('/edge-gateways/g1/certificate-rotations', data)
    expect(result).toEqual(cert)
  })
})
