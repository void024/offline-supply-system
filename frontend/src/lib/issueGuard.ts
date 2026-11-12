/**
 * issueGuard.ts
 * Responsibility: the offline issue guard from Sync Protocol rule 5, as pure functions over
 * locally visible events (confirmed AND own pending; quarantined never count).
 * Produces LOCAL ESTIMATES ONLY - never confirmed server truth. The guard never blocks factual
 * reporting: a handover that physically happened is recorded and flagged, not refused.
 * API calls: none.
 */

import type { EventType } from '../types'
import type { StoredEvent } from './localStore'

function visible(events: StoredEvent[]): StoredEvent[] {
  return events.filter((e) => e.sync_state !== 'quarantined')
}

/** Plan remaining, per plan = planned quantity - handovers already linked to it (clamped at 0). */
export function planRemaining(events: StoredEvent[], itemId: string): Map<string, number> {
  const remaining = new Map<string, number>()
  for (const e of visible(events)) {
    if (e.type === 'PLANNED_ISSUE' && e.item_id === itemId) {
      remaining.set(e.event_id, (remaining.get(e.event_id) ?? 0) + e.quantity)
    }
  }
  for (const e of visible(events)) {
    if (e.type === 'HANDOVER' && e.item_id === itemId && e.planned_issue_id !== null) {
      const left = remaining.get(e.planned_issue_id)
      if (left !== undefined) remaining.set(e.planned_issue_id, Math.max(0, left - e.quantity))
    }
  }
  return remaining
}

/** Local physical estimate = opening (0 baseline) + receipts - handovers + adjustments, locally visible only. */
export function localBalance(events: StoredEvent[], itemId: string): number {
  let balance = 0
  for (const e of visible(events)) {
    if (e.item_id !== itemId) continue
    if (e.type === 'RECEIPT') balance += e.quantity
    else if (e.type === 'HANDOVER') balance -= e.quantity
    else if (e.type === 'ADJUSTMENT') balance += e.quantity
  }
  return balance
}

/** Free to promise = max(0, local balance - sum of plan remaining). The unreserved stock. */
export function freeToPromise(events: StoredEvent[], itemId: string): number {
  let reserved = 0
  for (const left of planRemaining(events, itemId).values()) reserved += left
  return Math.max(0, localBalance(events, itemId) - reserved)
}

export interface GuardVerdict {
  /** false only for a new PLANNED_ISSUE above free-to-promise (planning guidance). */
  allowed: boolean
  /** Human-readable warning for handovers beyond estimates; recorded anyway (factual reporting). */
  warning: string | null
  /** The estimate figures, so the UI can label them as local estimates. */
  estimates: { localBalance: number; freeToPromise: number; planRemaining: number | null }
}

export function checkIssueGuard(
  events: StoredEvent[],
  type: EventType,
  itemId: string,
  quantity: number,
  plannedIssueId: string | null,
): GuardVerdict {
  const bal = localBalance(events, itemId)
  const free = freeToPromise(events, itemId)
  const estimates = { localBalance: bal, freeToPromise: free, planRemaining: null as number | null }

  if (type === 'PLANNED_ISSUE') {
    if (quantity > free) {
      return {
        allowed: false,
        warning: `Plan exceeds free to promise (local estimate ${free}). Plans guide work; reduce the quantity or record a receipt first.`,
        estimates,
      }
    }
    return { allowed: true, warning: null, estimates }
  }

  if (type === 'HANDOVER') {
    if (plannedIssueId !== null) {
      const left = planRemaining(events, itemId).get(plannedIssueId) ?? 0
      estimates.planRemaining = left
      if (quantity > Math.min(left, bal)) {
        return {
          allowed: true,
          warning: `This handover exceeds the plan's remaining ${left} and/or the local physical estimate ${bal} (local estimates). It is recorded as a fact and will be flagged for review - the guard never blocks factual reporting.`,
          estimates,
        }
      }
      return { allowed: true, warning: null, estimates }
    }
    if (quantity > free) {
      return {
        allowed: true,
        warning: `Unplanned handover exceeds free to promise (local estimate ${free}). It is recorded as a fact and will be flagged for review (UNPLANNED_HANDOVER) - the guard never blocks factual reporting.`,
        estimates,
      }
    }
    return { allowed: true, warning: null, estimates }
  }

  return { allowed: true, warning: null, estimates }
}
