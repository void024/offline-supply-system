/**
 * utils.ts
 * Responsibility: small shared helpers. No API calls.
 */

/** RFC 4122 v4 id from the platform crypto. Contract: client generates event_id / resolution_id. */
export function newId(): string {
  return crypto.randomUUID()
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return '-'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

export function toIsoNow(): string {
  return new Date().toISOString()
}
