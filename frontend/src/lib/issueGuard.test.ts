/**
 * issueGuard.test.ts
 * Verifies the Sync Protocol rule 5 guard semantics - in particular that fulfilling a plan is
 * capped by the plan's remaining and the physical estimate, NOT by free-to-promise (so fully
 * reserved stock can still be handed over), and that factual over-handovers warn but record.
 */

import { describe, expect, it } from 'vitest'
import { checkIssueGuard, freeToPromise, localBalance, planRemaining } from './issueGuard'
import type { StoredEvent } from './localStore'
import type { EventType, LocalSyncState } from '../types'

let n = 0
function mk(
  type: EventType,
  itemId: string,
  quantity: number,
  opts: { planned?: string | null; state?: LocalSyncState } = {},
): StoredEvent {
  n += 1
  return {
    event_id: `ev-${n}`,
    device_seq: n,
    recorded_by_user_id: 'user-1',
    type,
    item_id: itemId,
    quantity,
    planned_issue_id: opts.planned ?? null,
    reason: null,
    occurred_at: '2026-11-11T00:00:00.000Z',
    sync_state: opts.state ?? 'confirmed',
    status_detail: null,
    created_at: '2026-11-11T00:00:00.000Z',
    server_seq: n,
    origin: 'DEVICE',
    received_at: '2026-11-11T00:00:00.000Z',
  }
}

const ITEM = 'item-A'

describe('issue guard (Sync Protocol rule 5)', () => {
  it('fulfilling a fully-reserved plan is allowed: free-to-promise is 0 but the plan remaining funds it', () => {
    const receipt = mk('RECEIPT', ITEM, 10)
    const plan = mk('PLANNED_ISSUE', ITEM, 10)
    const events = [receipt, plan]
    expect(freeToPromise(events, ITEM)).toBe(0)
    const verdict = checkIssueGuard(events, 'HANDOVER', ITEM, 10, plan.event_id)
    expect(verdict.allowed).toBe(true)
    expect(verdict.warning).toBeNull()
    expect(verdict.estimates.planRemaining).toBe(10)
  })

  it('a linked handover decrements the plan once and physical once - remaining 0 after fulfillment, balance reduced once', () => {
    const receipt = mk('RECEIPT', ITEM, 10)
    const plan = mk('PLANNED_ISSUE', ITEM, 6)
    const handover = mk('HANDOVER', ITEM, 6, { planned: plan.event_id })
    const events = [receipt, plan, handover]
    expect(planRemaining(events, ITEM).get(plan.event_id)).toBe(0)
    expect(localBalance(events, ITEM)).toBe(4)
    expect(freeToPromise(events, ITEM)).toBe(4)
  })

  it('a linked handover beyond the plan remaining warns but still records (factual reporting)', () => {
    const receipt = mk('RECEIPT', ITEM, 10)
    const plan = mk('PLANNED_ISSUE', ITEM, 4)
    const verdict = checkIssueGuard([receipt, plan], 'HANDOVER', ITEM, 9, plan.event_id)
    expect(verdict.allowed).toBe(true)
    expect(verdict.warning).toMatch(/recorded as a fact/i)
  })

  it('a new plan above free-to-promise is blocked (planning guidance)', () => {
    const receipt = mk('RECEIPT', ITEM, 5)
    const plan = mk('PLANNED_ISSUE', ITEM, 3)
    const verdict = checkIssueGuard([receipt, plan], 'PLANNED_ISSUE', ITEM, 3, null)
    expect(verdict.allowed).toBe(false)
  })

  it('an unplanned handover above free-to-promise warns but records', () => {
    const receipt = mk('RECEIPT', ITEM, 2)
    const verdict = checkIssueGuard([receipt], 'HANDOVER', ITEM, 5, null)
    expect(verdict.allowed).toBe(true)
    expect(verdict.warning).toMatch(/UNPLANNED_HANDOVER/)
  })

  it('own pending events count (same device cannot spend the same units twice)', () => {
    const receipt = mk('RECEIPT', ITEM, 5, { state: 'pending' })
    const plan = mk('PLANNED_ISSUE', ITEM, 5, { state: 'pending' })
    expect(freeToPromise([receipt, plan], ITEM)).toBe(0)
  })

  it('quarantined records never count toward stock', () => {
    const rejected = mk('RECEIPT', ITEM, 100, { state: 'quarantined' })
    expect(localBalance([rejected], ITEM)).toBe(0)
    expect(freeToPromise([rejected], ITEM)).toBe(0)
  })
})
