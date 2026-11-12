/** items.types.ts — shapes for GET /api/v1/items (endpoint 5). */

export interface Item {
  id: string
  name: string
  unit: string
  /** Computed from the event log on every read. CAN be negative — surfaced, never suppressed. */
  balance: number
  /** max(0, planned - linked handovers). Over-delivery is surfaced separately as PLAN_EXCEEDED. */
  planned_outstanding: number
}

export interface ItemsResponse {
  items: Item[]
}
