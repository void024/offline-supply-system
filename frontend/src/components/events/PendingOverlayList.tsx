/**
 * PendingOverlayList.tsx
 * Responsibility: render the pending overlay - own records saved locally, not yet confirmed by
 * the server. Shown separately from confirmed stock, never merged into it (contract).
 * Props: events. API calls: none.
 */

import type { StoredEvent } from '../../lib/localStore'

interface Props {
  events: StoredEvent[]
}

export default function PendingOverlayList({ events }: Props) {
  if (events.length === 0) return <p>No pending records.</p>
  return (
    <ul>
      {events.map((e) => (
        <li key={e.event_id}>
          {e.type} {e.quantity} (item {e.item_id}) - waiting for server confirmation
        </li>
      ))}
    </ul>
  )
}
