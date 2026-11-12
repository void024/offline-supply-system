/**
 * IssuerPanel.tsx
 * Responsibility: render the issuer designation state and (for supervisors) the designate form.
 * Encoded business rules:
 *   - designation is fixed in v1; a 409 means one already exists and there is deliberately no
 *     re-designate endpoint (contract) - shown as text, not a retry loop;
 *   - losing the designated device's storage blocks issuing until a database-level reset
 *     (contract FLAG) - stated on screen next to the designation.
 * Props: issuer, thisDeviceId, canDesignate (supervisor), onDesignate, pending, serverError.
 * API calls: none (page owns the mutation).
 */

import { useState } from 'react'
import type { IssuerStatus } from '../../types'
import { formatDateTime } from '../../lib/utils'

interface Props {
  issuer: IssuerStatus
  thisDeviceId: string
  canDesignate: boolean
  pending: boolean
  serverError: string | null
  onDesignate: (deviceId: string) => void
}

export default function IssuerPanel({ issuer, thisDeviceId, canDesignate, pending, serverError, onDesignate }: Props) {
  const [deviceId, setDeviceId] = useState(thisDeviceId)
  const isThisDevice = issuer.device_id === thisDeviceId
  return (
    <section className="ledger-card issuer-card">
      <dl>
        <dt>Designated device</dt>
        <dd>{issuer.device_id ?? 'None designated yet'}</dd>
        <dt>Designated at</dt>
        <dd>{formatDateTime(issuer.designated_at)}</dd>
        <dt>This device</dt>
        <dd>
          {thisDeviceId}
          {issuer.device_id ? (isThisDevice ? ' - this IS the designated issuer' : ' - not the designated issuer') : ''}
        </dd>
      </dl>
      <p>
        Only the designated device may record planned issues and handovers, online or offline. The
        designation is fixed in v1. If the designated device loses its storage, issuing is blocked
        until a supervisor performs a database-level reset (a documented recovery procedure outside
        this app).
      </p>
      {canDesignate && issuer.device_id === null ? (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            onDesignate(deviceId)
          }}
          style={{ display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 420 }}
        >
          <label>
            Device ID to designate
            <input type="text" value={deviceId} onChange={(e) => setDeviceId(e.target.value)} />
          </label>
          <button type="submit" disabled={pending}>
            {pending ? 'Designating...' : 'Designate as issuer'}
          </button>
        </form>
      ) : null}
      {serverError ? <p role="alert">{serverError}</p> : null}
    </section>
  )
}
