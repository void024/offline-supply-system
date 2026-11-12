/** sync.types.ts — shapes for GET /api/v1/sync/pull (endpoint 4). */

import type { SupplyEvent } from './events.types'
import type { Item } from './items.types'
import type { Discrepancy } from './discrepancies.types'
import type { IssuerStatus } from './issuer.types'

/**
 * ASSUMPTION (flagged in the companion doc): the contract says submission_issues appear in the
 * pull reference snapshot and store the rejected submission verbatim, but does not enumerate the
 * row fields. Minimal shape below; Prompt 4 may add columns.
 */
export interface SubmissionIssue {
  claimed_event_id: string
  kind: string
  detail: string
}

export interface ReferenceSnapshot {
  items: Item[]
  issuer: IssuerStatus
  discrepancies: Discrepancy[]
  submission_issues: SubmissionIssue[]
}

export interface PullResponse {
  events: SupplyEvent[]
  /** server_seq of the LAST event in this page; on an empty page, the cursor the client sent. */
  next_cursor: number
  has_more: boolean
  reference: ReferenceSnapshot
  /** Snapshot high server_seq; apply reference only when has_more is false. */
  reference_as_of: number
}
