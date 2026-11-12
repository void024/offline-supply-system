/**
 * EventList.tsx
 * Responsibility: render movement-history rows from the append-only log, one stacked block per
 * event (all contract fields), so the history stays readable on a phone-width screen.
 * Props: events, showLocalState? (adds the local pending/quarantined line). API calls: none.
 */

import type { StoredEvent } from '../../lib/localStore'
import type { SupplyEvent } from '../../types'
import { formatDateTime } from '../../lib/utils'

interface Props {
  events: (SupplyEvent | StoredEvent)[]
  showLocalState?: boolean
}

function isStored(e: SupplyEvent | StoredEvent): e is StoredEvent {
  return 'sync_state' in e
}

export default function EventList({ events, showLocalState = false }: Props) {
  return (
    <div className="event-grid">
      {events.map((e) => (
        <section className="ledger-card" key={e.event_id}>
          <div className="card-kicker"><span className="badge neutral">{e.type.replaceAll('_', ' ')}</span><span>#{e.server_seq ?? 'LOCAL'}</span></div><dl>
            <dt>Server seq</dt>
            <dd>{e.server_seq ?? '-'}</dd>
            <dt>Type</dt>
            <dd>{e.type}</dd>
            <dt>Item</dt>
            <dd>{e.item_id}</dd>
            <dt>Quantity</dt>
            <dd>{e.quantity}</dd>
            <dt>Linked plan</dt>
            <dd>{e.planned_issue_id ?? '-'}</dd>
            <dt>Reason</dt>
            <dd>{e.reason ?? '-'}</dd>
            <dt>Occurred (device clock)</dt>
            <dd>{formatDateTime(e.occurred_at)}</dd>
            <dt>Origin</dt>
            <dd>{e.origin ?? '-'}</dd>
            {showLocalState && isStored(e) ? (
              <>
                <dt>Local state</dt>
                <dd>{e.sync_state}{e.status_detail ? ` (${e.status_detail})` : ''}</dd>
              </>
            ) : null}
          </dl>
        </section>
      ))}
    </div>
  )
}
