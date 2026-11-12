/**
 * localStore.test.ts
 * Verifies the device-local store: gapless device_seq allocation, pending-then-confirmed flow,
 * persistence across a simulated page reload, and idempotent pull-page application.
 * Runs against fake-indexeddb; no server involved.
 */

import { describe, expect, it } from 'vitest'
import {
  _closeDbForTests,
  applyPullPage,
  applyPushResults,
  getPullCursor,
  listLocalEvents,
  listPendingEvents,
  saveEventLocally,
} from './localStore'
import type { SupplyEvent } from '../types'

const base = {
  item_id: 'item-1',
  planned_issue_id: null,
  reason: null,
  occurred_at: '2026-11-11T00:00:00.000Z',
  recorded_by_user_id: 'user-1',
}

describe('local store', () => {
  it('allocates gapless device_seq and keeps records pending until pushed', async () => {
    const a = await saveEventLocally({ ...base, type: 'RECEIPT', quantity: 5 })
    const b = await saveEventLocally({ ...base, type: 'RECEIPT', quantity: 3 })
    const c = await saveEventLocally({ ...base, type: 'HANDOVER', quantity: 2 })
    expect([a.device_seq, b.device_seq, c.device_seq]).toEqual([1, 2, 3])
    expect((await listPendingEvents()).map((e) => e.event_id)).toEqual([a.event_id, b.event_id, c.event_id])
  })

  it('survives a simulated reload (close + reopen the database)', async () => {
    await _closeDbForTests()
    const events = await listLocalEvents()
    expect(events.length).toBe(3)
    expect(events.every((e) => e.sync_state === 'pending')).toBe(true)
  })

  it('push outcomes move records: ACCEPTED -> confirmed, QUARANTINED -> visible with reason', async () => {
    const [a, b, c] = await listPendingEvents()
    await applyPushResults([
      { event_id: a.event_id, status: 'ACCEPTED', detail: null },
      { event_id: b.event_id, status: 'DUPLICATE', detail: null },
      { event_id: c.event_id, status: 'QUARANTINED', detail: 'not the designated device' },
    ])
    const all = await listLocalEvents()
    expect(all.find((e) => e.event_id === a.event_id)?.sync_state).toBe('confirmed')
    expect(all.find((e) => e.event_id === b.event_id)?.sync_state).toBe('confirmed')
    const quar = all.find((e) => e.event_id === c.event_id)
    expect(quar?.sync_state).toBe('quarantined')
    expect(quar?.status_detail).toBe('not the designated device')
    expect((await listPendingEvents()).length).toBe(0)
  })

  it('applies a pull page and advances the cursor in one transaction; re-applying is idempotent', async () => {
    const pulled: SupplyEvent = {
      event_id: 'server-ev-1',
      device_seq: 9,
      recorded_by_user_id: 'user-2',
      type: 'RECEIPT',
      item_id: 'item-1',
      quantity: 40,
      planned_issue_id: null,
      reason: null,
      occurred_at: '2026-11-11T01:00:00.000Z',
      server_seq: 41,
      origin: 'DEVICE',
      received_at: '2026-11-11T01:00:01.000Z',
    }
    const page = {
      events: [pulled],
      next_cursor: 41,
      has_more: false,
      reference: { items: [], issuer: { device_id: null, designated_at: null }, discrepancies: [], submission_issues: [] },
      reference_as_of: 41,
    }
    await applyPullPage(page)
    expect(await getPullCursor()).toBe(41)
    await applyPullPage(page) // crash-before-cursor-advance replay must be a no-op
    const all = await listLocalEvents()
    expect(all.filter((e) => e.event_id === 'server-ev-1').length).toBe(1)
    expect(await getPullCursor()).toBe(41)
  })
})
