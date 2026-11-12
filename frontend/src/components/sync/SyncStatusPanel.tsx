/**
 * SyncStatusPanel.tsx
 * Responsibility: render the device-local sync status and the result of the last manual sync.
 * Props: status, lastSummary, onSyncNow, syncing, syncError. API calls: none (page owns the mutation).
 */

import { env } from '../../config/env'
import type { SyncStatus, SyncSummary } from '../../lib/syncEngine'

interface Props {
  status: SyncStatus
  lastSummary: SyncSummary | null
  syncing: boolean
  syncError: string | null
  onSyncNow: () => void
}

export default function SyncStatusPanel({ status, lastSummary, syncing, syncError, onSyncNow }: Props) {
  return (
    <section className="ledger-card sync-card"><p className="eyebrow">THIS DEVICE / SYNC CONTROL</p><h2>Keep the ledger in step.</h2><p>Push local records, then pull the latest confirmed state.</p>
      <dl>
        <dt>This device</dt>
        <dd>{status.device_id}</dd>
        <dt>Pull cursor (how far this device has synced)</dt>
        <dd>{status.cursor}</dd>
        <dt>Pending records (saved locally, not yet confirmed)</dt>
        <dd>{status.pendingCount}</dd>
        <dt>Quarantined records (rejected, still visible)</dt>
        <dd>{status.quarantinedCount}</dd>
      </dl>
      <button type="button" onClick={onSyncNow} disabled={syncing}>
        {syncing ? 'Syncing...' : 'Sync now'}
      </button>
      {syncError ? <p role="alert">{syncError}</p> : null}
      {lastSummary ? (
        <p>
          Last sync: pushed {lastSummary.push?.results.length ?? 0} record(s), pulled{' '}
          {lastSummary.pull.events} event(s) in {lastSummary.pull.pages} page(s); cursor now{' '}
          {lastSummary.pull.cursor}
          {env.demoMode ? ' (simulated demo server - no real backend)' : ''}.
        </p>
      ) : null}
    </section>
  )
}
