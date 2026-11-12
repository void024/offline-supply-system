/**
 * QuarantinedList.tsx
 * Responsibility: render the quarantined overlay - records the server rejected, with the reason.
 * They stay visible, are never silently erased, and never count toward stock (contract).
 * Props: events. API calls: none.
 */

import type { StoredEvent } from '../../lib/localStore'

interface Props {
  events: StoredEvent[]
}

export default function QuarantinedList({ events }: Props) {
  if (events.length === 0) return <p>No quarantined records.</p>
  return (
    <ul>
      {events.map((e) => (
        <li key={e.event_id}>
          {e.type} {e.quantity} (item {e.item_id}) - rejected: {e.status_detail ?? 'no reason given'}
        </li>
      ))}
    </ul>
  )
}
