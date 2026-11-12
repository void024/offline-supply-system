/**
 * syncEngine.ts
 * Responsibility: the device-side sync protocol (Prompt 2, Sync Protocol rules 1-4).
 *   1. save locally (pending)   - localStore.saveEventLocally
 *   2. push pending in order    - eventsApi.pushEvents, then applyPushResults
 *   3. pull pages + cursor      - syncApi.pull, applyPullPage per page, loop while has_more
 *   4. retry always safe        - same event_ids -> DUPLICATE; cursor only advances per applied page
 * Hooks call this engine; the engine calls the api layer and localStore. No component calls it.
 * API calls: via events.api.ts and sync.api.ts only.
 */

import * as eventsApi from '../api/events.api'
import * as syncApi from '../api/sync.api'
import * as localStore from './localStore'
import type { EventInputDto, PushEventsResponse } from '../types'

const BATCH_LIMIT = 500 // contract input limit

export interface RecordOutcome {
  event: localStore.StoredEvent
  /** true when the push attempt in the same call reached the server and cleared the flag. */
  pushedNow: boolean
}

export async function recordEvent(input: localStore.NewLocalEvent): Promise<RecordOutcome> {
  const event = await localStore.saveEventLocally(input)
  try {
    await pushPending()
    const after = await localStore.listPendingEvents()
    return { event, pushedNow: !after.some((e) => e.event_id === event.event_id) }
  } catch {
    // Offline or server unreachable: the record stays pending locally. Not an error for the user.
    return { event, pushedNow: false }
  }
}

export async function pushPending(): Promise<PushEventsResponse | null> {
  const pending = await localStore.listPendingEvents()
  if (pending.length === 0) return null
  const batch: EventInputDto[] = pending.slice(0, BATCH_LIMIT).map((e) => ({
    event_id: e.event_id,
    device_seq: e.device_seq ?? 0,
    recorded_by_user_id: e.recorded_by_user_id ?? '',
    type: e.type,
    item_id: e.item_id,
    quantity: e.quantity,
    planned_issue_id: e.planned_issue_id,
    reason: e.reason,
    occurred_at: e.occurred_at,
  }))
  const response = await eventsApi.pushEvents({ events: batch })
  await localStore.applyPushResults(response.results)
  return response
}

export interface PullSummary {
  pages: number
  events: number
  cursor: number
}

export async function pullAll(): Promise<PullSummary> {
  let pages = 0
  let events = 0
  let cursor = await localStore.getPullCursor()
  for (;;) {
    const page = await syncApi.pullEvents({ after: cursor, limit: BATCH_LIMIT })
    await localStore.applyPullPage(page)
    pages += 1
    events += page.events.length
    cursor = page.next_cursor
    if (!page.has_more) break
  }
  // Contract rule 6: one full pull from the cursor is the bootstrap; only then is the device usable.
  await localStore.markBootstrapped()
  return { pages, events, cursor }
}

export interface SyncSummary {
  push: PushEventsResponse | null
  pull: PullSummary
}

export async function syncNow(): Promise<SyncSummary> {
  const push = await pushPending()
  const pull = await pullAll()
  return { push, pull }
}

export interface SyncStatus {
  cursor: number
  pendingCount: number
  quarantinedCount: number
  device_id: string
  bootstrapped: boolean
}

export async function getStatus(): Promise<SyncStatus> {
  const [cursor, pending, quarantined, identity, bootstrapped] = await Promise.all([
    localStore.getPullCursor(),
    localStore.listPendingEvents(),
    localStore.listQuarantinedEvents(),
    localStore.getDeviceIdentity(),
    localStore.isBootstrapped(),
  ])
  return {
    cursor,
    pendingCount: pending.length,
    quarantinedCount: quarantined.length,
    device_id: identity.device_id,
    bootstrapped,
  }
}
