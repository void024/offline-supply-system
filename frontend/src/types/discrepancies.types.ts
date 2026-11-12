/** discrepancies.types.ts — shapes for GET /api/v1/discrepancies (endpoint 7) and POST .../resolve (endpoint 8). */

export type DiscrepancyType =
  | 'NEGATIVE_BALANCE'
  | 'UNPLANNED_HANDOVER'
  | 'PLAN_EXCEEDED'
  | 'UNRESOLVED_REFERENCE'

export type DiscrepancyStatus = 'OPEN' | 'RESOLVED'

export type ResolutionOutcome = 'NO_CHANGE' | 'ADJUSTMENT'

/**
 * ASSUMPTION (flagged in the companion doc): the contract shows resolution as an embedded object
 * or null without enumerating fields. Fields below follow from the resolve request body plus a
 * timestamp; Prompt 4 may extend them.
 */
export interface ResolutionRecord {
  resolution_id: string
  outcome: ResolutionOutcome
  reason: string
  quantity_delta: number | null
  resolved_at: string
}

export interface Discrepancy {
  id: string
  item_id: string
  type: DiscrepancyType
  details: string
  status: DiscrepancyStatus
  detected_at: string
  resolution: ResolutionRecord | null
}

export interface DiscrepanciesResponse {
  discrepancies: Discrepancy[]
}

export interface ResolveDto {
  /** Client-generated idempotency key; also becomes the ADJUSTMENT event's event_id. */
  resolution_id: string
  outcome: ResolutionOutcome
  reason: string
  /** Required when outcome is ADJUSTMENT; null for NO_CHANGE. */
  quantity_delta: number | null
}

export interface ResolveResponse {
  resolution_id: string
  status: 'RESOLVED'
  adjustment_event_id: string | null
}
