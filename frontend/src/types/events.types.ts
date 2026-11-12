/** events.types.ts — shapes for POST /api/v1/events (endpoint 3) and GET /api/v1/events history browse (endpoint 6). */

export type EventType = 'RECEIPT' | 'PLANNED_ISSUE' | 'HANDOVER' | 'ADJUSTMENT'

export type PushStatus = 'ACCEPTED' | 'DUPLICATE' | 'CONFLICT' | 'QUARANTINED'

export type EventOrigin = 'DEVICE' | 'SERVER'

export interface EventInputDto {
  event_id: string
  device_seq: number
  /** Unverified claim, fixed at creation. Displayed as a claim; never authorizes anything (contract). */
  recorded_by_user_id: string
  type: EventType
  item_id: string
  /** > 0 for RECEIPT / PLANNED_ISSUE / HANDOVER; any non-zero for ADJUSTMENT. */
  quantity: number
  /** Set when a HANDOVER fulfills a plan; null otherwise. Soft link, not a foreign key. */
  planned_issue_id: string | null
  /** Required for ADJUSTMENT; null otherwise. */
  reason: string | null
  /** ISO-8601 device clock. Advisory only; server_seq is the ordering the system relies on. */
  occurred_at: string
}

export interface PushEventsRequest {
  events: EventInputDto[]
}

export interface EventResult {
  event_id: string
  status: PushStatus
  detail: string | null
}

export interface PushEventsResponse {
  results: EventResult[]
}

/**
 * Server-delivered event: the device input plus server-assigned provenance.
 * SERVER-origin events (supervisor adjustments written by a resolution) carry NULL
 * device_seq and recorded_by_user_id per the contract's provenance CHECK, so both
 * are nullable here.
 */
export interface SupplyEvent extends Omit<EventInputDto, 'device_seq' | 'recorded_by_user_id'> {
  device_seq: number | null
  recorded_by_user_id: string | null
  server_seq: number
  origin: EventOrigin
  received_at: string
}

export interface EventHistoryParams {
  item_id?: string
  type?: EventType
  after?: number
  limit?: number
}

/**
 * ASSUMPTION (flagged in the Prompt 3 companion doc): the Prompt 2 contract specifies the query
 * parameters for GET /api/v1/events but not the response envelope. This shape mirrors the event
 * array used by the sync pull. If Prompt 4 picks a different envelope, only this type changes.
 */
export interface EventHistoryResponse {
  events: SupplyEvent[]
}
