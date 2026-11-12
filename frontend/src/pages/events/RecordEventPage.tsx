/**
 * RecordEventPage
 * Route          : /record
 * Responsibility : record one movement event - saved to the local store first (pending), then
 *                  pushed; the record never disappears if the network is down (contract rules 1-2).
 * API calls      : useRecordEvent (sync engine -> POST /events), useItems, useLocalOverlays, useIssuer
 * Auth required  : Yes
 */

import { useState } from 'react'
import EventForm from '../../components/events/EventForm'
import LoadingSpinner from '../../components/common/LoadingSpinner'
import ErrorMessage from '../../components/common/ErrorMessage'
import EmptyState from '../../components/common/EmptyState'
import { useItems, useLocalOverlays } from '../../hooks/useItems'
import { useAllLocalEvents, useRecordEvent } from '../../hooks/useEvents'
import { useIssuer } from '../../hooks/useIssuer'
import { useAuth } from '../../hooks/useAuth'
import { useSyncNow, useSyncStatus } from '../../hooks/useSync'
import { env } from '../../config/env'
import type { NewLocalEvent } from '../../lib/localStore'

export default function RecordEventPage() {
  const { user } = useAuth()
  const items = useItems()
  const overlays = useLocalOverlays()
  const localEvents = useAllLocalEvents()
  const issuer = useIssuer()
  const status = useSyncStatus()
  const syncNow = useSyncNow()
  const record = useRecordEvent()
  const [savedNote, setSavedNote] = useState<string | null>(null)

  if (items.isLoading || overlays.isLoading || localEvents.isLoading || issuer.isLoading || status.isLoading) return <LoadingSpinner />
  if (items.isError) return <ErrorMessage message={items.error?.message} />
  if (!items.data || items.data.items.length === 0) {
    return <EmptyState message="No items found. Items are seeded by the server." />
  }
  if (!user) return <ErrorMessage message="No signed-in user." />

  // Contract rule 6: recording is gated on bootstrap. Without one full pull the device has no
  // authoritative opening balance, so local estimates would silently start from zero.
  if (status.data && !status.data.bootstrapped) {
    return (
      <main className="workspace">
        <p className="eyebrow">FIELD OPERATIONS / SUPPLY LEDGER</p>
      <h1>Record a movement</h1>
        <p role="alert">
          First sync required. This device has not completed its first full sync, so it has no
          authoritative opening stock. Recording stays closed until that finishes - otherwise local
          estimates would wrongly start from zero.
        </p>
        <button type="button" onClick={() => syncNow.mutate()} disabled={syncNow.isPending}>
          {syncNow.isPending ? 'Running first sync...' : 'Run first sync now'}
        </button>
        {syncNow.isError ? <p role="alert">{syncNow.error.message}</p> : null}
      </main>
    )
  }

  const allLocal = localEvents.data ?? []
  const designatedDeviceId = issuer.data?.device_id ?? null
  const thisDeviceId = status.data?.device_id ?? ''
  const isDesignatedDevice = designatedDeviceId !== null && designatedDeviceId === thisDeviceId

  const submit = (input: NewLocalEvent) => {
    setSavedNote(null)
    record.mutate(input, {
      onSuccess: (outcome) => {
        setSavedNote(
          outcome.pushedNow
            ? env.demoMode
              ? 'Saved and confirmed (simulated demo server - no real backend).'
              : 'Saved and confirmed by the server.'
            : 'Saved on this device. Open Sync and choose Sync now when the server is reachable.',
        )
      },
    })
  }

  return (
    <main className="workspace">
      <h1>Record a movement</h1>
      {designatedDeviceId === null ? (
        <p role="alert">
          No issuer device is designated yet. Receipts can be recorded; planned issues and handovers
          need a supervisor to designate an issuer first.
        </p>
      ) : null}
      <EventForm
        items={items.data.items}
        localEvents={allLocal}
        isDesignatedDevice={isDesignatedDevice}
        role={user.role}
        currentUserId={user.user_id}
        pending={record.isPending}
        onSubmit={submit}
      />
      {savedNote ? <p role="status">{savedNote}</p> : null}
    </main>
  )
}
