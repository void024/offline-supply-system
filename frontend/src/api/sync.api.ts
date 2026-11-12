/**
 * sync.api.ts
 * Responsibility: typed function for the sync resource group (contract endpoint 4).
 * API calls: GET /sync/pull.
 */

import client from './client'
import { env } from '../config/env'
import * as demo from '../demo'
import type { PullResponse } from '../types'

export async function pullEvents(params: { after: number; limit?: number }): Promise<PullResponse> {
  if (env.demoMode) return demo.pullEvents(params)
  const { data } = await client.get<PullResponse>('/sync/pull', {
    params: { after: params.after, limit: params.limit ?? 500 },
  })
  return data
}
