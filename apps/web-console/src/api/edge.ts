// Function: Edge gateway identity, activation, heartbeat and certificate API
// Time: 2026-07-10
// Author: AxeXie
import client from './client'
import type {
  EdgeGateway,
  EdgeGatewayCertificate,
  CreateEdgeGatewayRequest,
  ActivateEdgeGatewayRequest,
  RevokeEdgeGatewayRequest,
  EdgeGatewayHeartbeatRequest,
  RotateCertificateRequest,
  EdgeGatewayListParams,
  PageResult
} from '@/types/api'

/** GET /edge-gateways — paginated edge gateway list */
export async function listEdgeGateways(
  params: EdgeGatewayListParams
): Promise<PageResult<EdgeGateway>> {
  const response = await client.get<PageResult<EdgeGateway>>('/edge-gateways', { params })
  return response.data
}

/** GET /edge-gateways/{gatewayId} — fetch a single edge gateway */
export async function getEdgeGateway(gatewayId: string): Promise<EdgeGateway> {
  const response = await client.get<EdgeGateway>(`/edge-gateways/${gatewayId}`)
  return response.data
}

/** POST /edge-gateways — register a new edge gateway */
export async function registerEdgeGateway(
  data: CreateEdgeGatewayRequest
): Promise<EdgeGateway> {
  const response = await client.post<EdgeGateway>('/edge-gateways', data)
  return response.data
}

/** POST /edge-gateways/{gatewayId}/activate — activate a gateway */
export async function activateEdgeGateway(
  gatewayId: string,
  data?: ActivateEdgeGatewayRequest
): Promise<EdgeGateway> {
  const response = await client.post<EdgeGateway>(
    `/edge-gateways/${gatewayId}/activate`,
    data ?? {}
  )
  return response.data
}

/** POST /edge-gateways/{gatewayId}/revoke — revoke a gateway */
export async function revokeEdgeGateway(
  gatewayId: string,
  data?: RevokeEdgeGatewayRequest
): Promise<EdgeGateway> {
  const response = await client.post<EdgeGateway>(
    `/edge-gateways/${gatewayId}/revoke`,
    data ?? {}
  )
  return response.data
}

/** POST /edge-gateways/{gatewayId}/heartbeat — record a heartbeat */
export async function heartbeatEdgeGateway(
  gatewayId: string,
  data?: EdgeGatewayHeartbeatRequest
): Promise<EdgeGateway> {
  const response = await client.post<EdgeGateway>(
    `/edge-gateways/${gatewayId}/heartbeat`,
    data ?? {}
  )
  return response.data
}

/** POST /edge-gateways/{gatewayId}/certificate-rotations — rotate certificate */
export async function rotateCertificate(
  gatewayId: string,
  data: RotateCertificateRequest
): Promise<EdgeGatewayCertificate> {
  const response = await client.post<EdgeGatewayCertificate>(
    `/edge-gateways/${gatewayId}/certificate-rotations`,
    data
  )
  return response.data
}
