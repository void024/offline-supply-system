/**
 * events.api.ts
 * Responsibility: typed functions for the events resource group (contract endpoints 3 and 6).
 * API calls: POST /events (the only write path for movement events), GET /events (history browse).
 */

import client from './client'
import { env } from '../config/env'
import * as demo from '../demo'
import type {
  EventHistoryParams,
  EventHistoryResponse,
  PushEventsRequest,
  PushEventsResponse,
} from '../types'

export async function pushEvents(body: PushEventsRequest): Promise<PushEventsResponse> {
  if (env.demoMode) return demo.pushEvents(body)
  const { data } = await client.post<PushEventsResponse>('/events', body)
  return data
}

export async function listEventHistory(params: EventHistoryParams = {}): Promise<EventHistoryResponse> {
  if (env.demoMode) return demo.listEventHistory(params)
  const { data } = await client.get<EventHistoryResponse>('/events', {
    params: { item_id: params.item_id, type: params.type, after: params.after, limit: params.limit ?? 100 },
  })
  return data
}
