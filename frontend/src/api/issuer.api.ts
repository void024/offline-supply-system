/**
 * issuer.api.ts
 * Responsibility: typed functions for the issuer resource group (contract endpoints 9-10).
 * API calls: GET /issuer, POST /issuer/designate.
 */

import client from './client'
import { env } from '../config/env'
import * as demo from '../demo'
import type { DesignateDto, DesignateResponse, IssuerStatus } from '../types'

export async function getIssuer(): Promise<IssuerStatus> {
  if (env.demoMode) return demo.getIssuer()
  const { data } = await client.get<IssuerStatus>('/issuer')
  return data
}

export async function designateIssuer(dto: DesignateDto): Promise<DesignateResponse> {
  if (env.demoMode) return demo.designateIssuer(dto)
  const { data } = await client.post<DesignateResponse>('/issuer/designate', dto)
  return data
}
