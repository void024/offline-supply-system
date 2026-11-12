/**
 * SyncPage
 * Route          : /sync
 * Responsibility : device-local sync status (cursor, pending, quarantined) and a manual
 *                  "Sync now" that runs the contract's push-then-pull cycle (rules 2-3).
 * API calls      : useSyncStatus (local store), useSyncNow (POST /events + GET /sync/pull),
 *                  useLocalOverlays (local store)
 * Auth required  : Yes
 */

import { useState } from 'react'
import SyncStatusPanel from '../../components/sync/SyncStatusPanel'
import PendingOverlayList from '../../components/events/PendingOverlayList'
import QuarantinedList from '../../components/events/QuarantinedList'
import LoadingSpinner from '../../components/common/LoadingSpinner'
import ErrorMessage from '../../components/common/ErrorMessage'
import { useSyncNow, useSyncStatus } from '../../hooks/useSync'
import { useLocalOverlays } from '../../hooks/useItems'
import type { SyncSummary } from '../../lib/syncEngine'

export default function SyncPage() {
  const status = useSyncStatus()
  const overlays = useLocalOverlays()
  const syncNow = useSyncNow()
  const [lastSummary, setLastSummary] = useState<SyncSummary | null>(null)

  if (status.isLoading || overlays.isLoading) return <LoadingSpinner />
  if (status.isError) return <ErrorMessage message={status.error?.message} />
  if (!status.data) return <ErrorMessage message="Sync status unavailable." />

  return (
    <main className="workspace">
      <p className="eyebrow">FIELD OPERATIONS / SUPPLY LEDGER</p>
      <h1>Sync</h1>
      <SyncStatusPanel
        status={status.data}
        lastSummary={lastSummary}
        syncing={syncNow.isPending}
        syncError={syncNow.isError ? syncNow.error.message : null}
        onSyncNow={() =>
          syncNow.mutate(undefined, {
            onSuccess: (summary) => setLastSummary(summary),
          })
        }
      />
      <h2>Pending records</h2>
      <PendingOverlayList events={overlays.data?.pending ?? []} />
      <h2>Quarantined records</h2>
      <QuarantinedList events={overlays.data?.quarantined ?? []} />
    </main>
  )
}
