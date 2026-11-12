/**
 * DiscrepancyList.tsx
 * Responsibility: render discrepancies, one stacked block per row (all contract fields), so the
 * list stays readable on a phone-width screen - a wide table cannot reflow without styling,
 * which this stage forbids. Same information, narrow-safe structure.
 * Props: discrepancies, canResolve (supervisor). API calls: none.
 */

import { Link } from 'react-router-dom'
import type { Discrepancy } from '../../types'
import { formatDateTime } from '../../lib/utils'

interface Props {
  discrepancies: Discrepancy[]
  canResolve: boolean
}

export default function DiscrepancyList({ discrepancies, canResolve }: Props) {
  return (
    <div className="review-grid">
      {discrepancies.map((d) => (
        <section className="ledger-card" key={d.id}>
          <div className="card-kicker"><span className="badge danger">{d.status}</span><span>REVIEW REQUIRED</span></div><h2>{d.type.replaceAll('_', ' ').toLowerCase()}</h2><dl>
            <dt>Type</dt>
            <dd>{d.type}</dd>
            <dt>Item</dt>
            <dd>{d.item_id}</dd>
            <dt>Details</dt>
            <dd>{d.details}</dd>
            <dt>Status</dt>
            <dd>{d.status}</dd>
            <dt>Detected</dt>
            <dd>{formatDateTime(d.detected_at)}</dd>
            <dt>Resolution</dt>
            <dd>{d.resolution ? `${d.resolution.outcome}: ${d.resolution.reason}` : '-'}</dd>
          </dl>
          {canResolve && d.status === 'OPEN' ? (
            <Link className="button-link secondary" to={`/discrepancies/${d.id}/resolve`}>Review discrepancy <span aria-hidden="true">↗</span></Link>
          ) : null}
        </section>
      ))}
    </div>
  )
}
