/**
 * local.types.ts — device-local records (Prompt 2, CLIENT: device-app section).
 * These never leave the device except through POST /api/v1/events.
 */

import type { EventInputDto } from './events.types'

export type LocalSyncState = 'pending' | 'confirmed' | 'quarantined'

export interface LocalEvent extends EventInputDto {
  sync_state: LocalSyncState
  /** Server's reason when quarantined/conflicted; null otherwise. */
  status_detail: string | null
  created_at: string
}

/** Keys of the single-row meta store (contract: next device_seq, pull cursor, device_id, device_key). */
export interface DeviceMeta {
  device_id: string
  device_key: string | null
  next_device_seq: number
  pull_cursor: number
}
