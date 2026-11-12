/**
 * EventForm.tsx
 * Responsibility: controlled form for recording one movement event (contract endpoint 3 fields).
 * Encoded business rules:
 *   - PLANNED_ISSUE / HANDOVER are offered only on the designated device (UI convenience; the
 *     server enforces it for real).
 *   - ADJUSTMENT is offered only to supervisors.
 *   - The issue guard (Sync Protocol rule 5) is computed live and shown as LOCAL ESTIMATES.
 *     Plans above free-to-promise are blocked (planning guidance); handovers beyond estimates
 *     warn but still record - the guard never blocks factual reporting.
 * Props: items, localEvents, isDesignatedDevice, role, currentUserId, onSubmit, pending.
 * API calls: none (the page owns the mutation).
 */

import { useMemo, useState } from 'react'
import type { EventType, Item, Role } from '../../types'
import type { NewLocalEvent, StoredEvent } from '../../lib/localStore'
import { checkIssueGuard, planRemaining } from '../../lib/issueGuard'
import { toIsoNow } from '../../lib/utils'

interface Props {
  items: Item[]
  localEvents: StoredEvent[]
  isDesignatedDevice: boolean
  role: Role
  currentUserId: string
  pending: boolean
  onSubmit: (input: NewLocalEvent) => void
}

const ALL_TYPES: EventType[] = ['RECEIPT', 'PLANNED_ISSUE', 'HANDOVER', 'ADJUSTMENT']

export default function EventForm({
  items,
  localEvents,
  isDesignatedDevice,
  role,
  currentUserId,
  pending,
  onSubmit,
}: Props) {
  const allowedTypes = ALL_TYPES.filter((t) => {
    if (t === 'PLANNED_ISSUE' || t === 'HANDOVER') return isDesignatedDevice
    if (t === 'ADJUSTMENT') return role === 'supervisor'
    return true
  })

  const [type, setType] = useState<EventType>('RECEIPT')
  const [itemId, setItemId] = useState<string>(items[0]?.id ?? '')
  const [quantity, setQuantity] = useState<string>('1')
  const [plannedIssueId, setPlannedIssueId] = useState<string>('')
  const [reason, setReason] = useState<string>('')
  const [validation, setValidation] = useState<string | null>(null)

  const openPlans = useMemo(() => {
    if (type !== 'HANDOVER' || !itemId) return []
    return [...planRemaining(localEvents, itemId).entries()].filter(([, left]) => left > 0)
  }, [type, itemId, localEvents])

  const qty = Number(quantity)
  const guard =
    itemId && Number.isFinite(qty) && qty !== 0
      ? checkIssueGuard(localEvents, type, itemId, qty, plannedIssueId || null)
      : null

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setValidation(null)
    if (!itemId) return setValidation('Choose an item.')
    if (!Number.isInteger(qty)) return setValidation('Quantity must be a whole number.')
    if (type === 'ADJUSTMENT') {
      if (qty === 0) return setValidation('An adjustment cannot be zero.')
      if (!reason.trim()) return setValidation('An adjustment needs a reason.')
    } else if (qty <= 0) {
      return setValidation('Quantity must be positive.')
    }
    if (guard && !guard.allowed) return setValidation(guard.warning)
    onSubmit({
      type,
      item_id: itemId,
      quantity: qty,
      planned_issue_id: type === 'HANDOVER' && plannedIssueId ? plannedIssueId : null,
      reason: type === 'ADJUSTMENT' ? reason.trim() : null,
      occurred_at: toIsoNow(),
      recorded_by_user_id: currentUserId,
    })
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 420 }}>
      {allowedTypes.length === 0 ? (
        <p role="alert">
          This device may not record issues (it is not the designated issuer) and your role may not
          record adjustments. Only a designated device or a supervisor session can record here.
        </p>
      ) : null}
      <label>
        Record type
        <select value={type} onChange={(e) => setType(e.target.value as EventType)}>
          {allowedTypes.map((t) => (
            <option key={t} value={t}>
              {t.replaceAll('_', ' ').toLowerCase()}
            </option>
          ))}
        </select>
      </label>
      <label>
        Item
        <select value={itemId} onChange={(e) => setItemId(e.target.value)}>
          {items.map((i) => (
            <option key={i.id} value={i.id}>
              {i.name} ({i.unit})
            </option>
          ))}
        </select>
      </label>
      <label>
        Quantity
        <input type="number" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
      </label>
      {type === 'HANDOVER' ? (
        <label>
          Fulfills plan (optional)
          <select value={plannedIssueId} onChange={(e) => setPlannedIssueId(e.target.value)}>
            <option value="">Unplanned handover</option>
            {openPlans.map(([planId, left]) => (
              <option key={planId} value={planId}>
                Plan {planId.slice(0, 8)}... ({left} remaining, local estimate)
              </option>
            ))}
          </select>
        </label>
      ) : null}
      {type === 'ADJUSTMENT' ? (
        <label>
          Reason (required)
          <input type="text" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
      ) : null}
      {guard ? (
        <p>
          Local estimates (not confirmed server truth): balance {guard.estimates.localBalance}, free to
          promise {guard.estimates.freeToPromise}
          {guard.estimates.planRemaining !== null ? `, plan remaining ${guard.estimates.planRemaining}` : ''}.
        </p>
      ) : null}
      {guard?.warning ? <p role="alert">{guard.warning}</p> : null}
      {validation ? <p role="alert">{validation}</p> : null}
      <button type="submit" disabled={pending || allowedTypes.length === 0}>
        {pending ? 'Saving...' : 'Save record'}
      </button>
    </form>
  )
}
