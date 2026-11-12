/**
 * ResolveForm.tsx
 * Responsibility: controlled form for the reasoned-outcome review (contract endpoint 8).
 * Encoded business rules:
 *   - reason is required for BOTH outcomes (NO_CHANGE and ADJUSTMENT);
 *   - quantity_delta only exists for ADJUSTMENT;
 *   - the resolution_id idempotency key is generated once per form mount and reused on retry,
 *     so a lost response retried by the user cannot double-apply (contract).
 * Props: onSubmit(dto), pending, serverError. API calls: none (page owns the mutation).
 */

import { useMemo, useState } from 'react'
import type { ResolutionOutcome, ResolveDto } from '../../types'
import { newId } from '../../lib/utils'

interface Props {
  pending: boolean
  serverError: string | null
  onSubmit: (dto: ResolveDto) => void
}

export default function ResolveForm({ pending, serverError, onSubmit }: Props) {
  const resolutionId = useMemo(() => newId(), [])
  const [outcome, setOutcome] = useState<ResolutionOutcome>('NO_CHANGE')
  const [reason, setReason] = useState('')
  const [delta, setDelta] = useState<string>('0')
  const [validation, setValidation] = useState<string | null>(null)

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setValidation(null)
    if (!reason.trim()) return setValidation('Every outcome needs a reason.')
    if (outcome === 'ADJUSTMENT') {
      const n = Number(delta)
      if (!Number.isInteger(n) || n === 0) return setValidation('An adjustment needs a non-zero whole-number change.')
      onSubmit({ resolution_id: resolutionId, outcome, reason: reason.trim(), quantity_delta: n })
    } else {
      onSubmit({ resolution_id: resolutionId, outcome, reason: reason.trim(), quantity_delta: null })
    }
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 420 }}>
      <fieldset>
        <legend>Outcome</legend>
        <label>
          <input
            type="radio"
            name="outcome"
            checked={outcome === 'NO_CHANGE'}
            onChange={() => setOutcome('NO_CHANGE')}
          />
          Explained - no stock change (e.g. a delayed receipt explains it)
        </label>
        <br />
        <label>
          <input
            type="radio"
            name="outcome"
            checked={outcome === 'ADJUSTMENT'}
            onChange={() => setOutcome('ADJUSTMENT')}
          />
          Adjust stock (only when a correction is justified)
        </label>
      </fieldset>
      <label>
        Reason (required)
        <input type="text" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      {outcome === 'ADJUSTMENT' ? (
        <label>
          Stock change (+/- units)
          <input type="number" value={delta} onChange={(e) => setDelta(e.target.value)} />
        </label>
      ) : null}
      {validation ? <p role="alert">{validation}</p> : null}
      {serverError ? <p role="alert">{serverError}</p> : null}
      <button type="submit" disabled={pending}>
        {pending ? 'Recording...' : 'Record review'}
      </button>
    </form>
  )
}
