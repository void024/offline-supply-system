/**
 * items.api.ts
 * Responsibility: typed function for the items resource group (contract endpoint 5).
 * API calls: GET /items.
 */

import client from './client'
import { env } from '../config/env'
import * as demo from '../demo'
import type { ItemsResponse } from '../types'

export async function listItems(): Promise<ItemsResponse> {
  if (env.demoMode) return demo.listItems()
  const { data } = await client.get<ItemsResponse>('/items')
  return data
}
