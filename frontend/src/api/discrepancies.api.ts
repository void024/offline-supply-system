/**
 * discrepancies.api.ts
 * Responsibility: typed functions for the discrepancies resource group (contract endpoints 7-8).
 * API calls: GET /discrepancies, POST /discrepancies/{id}/resolve.
 */

import client from './client'
import { env } from '../config/env'
import * as demo from '../demo'
import type {
  DiscrepanciesResponse,
  DiscrepancyStatus,
  ResolveDto,
  ResolveResponse,
} from '../types'

export async function listDiscrepancies(status: DiscrepancyStatus = 'OPEN'): Promise<DiscrepanciesResponse> {
  if (env.demoMode) return demo.listDiscrepancies(status)
  const { data } = await client.get<DiscrepanciesResponse>('/discrepancies', { params: { status } })
  return data
}

export async function resolveDiscrepancy(id: string, dto: ResolveDto): Promise<ResolveResponse> {
  if (env.demoMode) return demo.resolveDiscrepancy(id, dto)
  const { data } = await client.post<ResolveResponse>(`/discrepancies/${id}/resolve`, dto)
  return data
}
