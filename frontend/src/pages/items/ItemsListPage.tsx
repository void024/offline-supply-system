/**
 * ItemsListPage
 * Route          : /
 * Responsibility : current stock view - confirmed balance with pending and quarantined records
 *                  shown as separate overlays, never merged (contract, CLIENT section).
 * API calls      : useItems (GET /items), useLocalOverlays (device-local store)
 * Auth required  : Yes
 */

import { Link } from 'react-router-dom'
import ItemList from '../../components/items/ItemList'
import PendingOverlayList from '../../components/events/PendingOverlayList'
import QuarantinedList from '../../components/events/QuarantinedList'
import LoadingSpinner from '../../components/common/LoadingSpinner'
import ErrorMessage from '../../components/common/ErrorMessage'
import EmptyState from '../../components/common/EmptyState'
import { useItems, useLocalOverlays } from '../../hooks/useItems'

export default function ItemsListPage() {
  const { data, isLoading, isError, error } = useItems()
  const overlays = useLocalOverlays()
  if (isLoading) return <LoadingSpinner />
  if (isError) return <ErrorMessage message={error?.message} />
  if (!data || data.items.length === 0) return <EmptyState message="No items found. Items are seeded by the server." />
  return (
    <main className="workspace">
      <p className="eyebrow">FIELD OPERATIONS / SUPPLY LEDGER</p>
      <div className="page-heading"><div><h1>Current stock<span className="heading-dot">.</span></h1><p>Server-confirmed balances. Every unit has a history.</p></div><Link className="button-link" to="/record">+ Record movement</Link></div>
      <div className="stock-summary"><div><span className="eyebrow">TRACKED ITEMS</span><strong>{data.items.length.toString().padStart(2, '0')}</strong></div><div><span className="eyebrow">NEGATIVE BALANCES</span><strong className={data.items.some(i => i.balance < 0) ? 'danger-text' : ''}>{data.items.filter(i => i.balance < 0).length.toString().padStart(2, '0')}</strong></div><p>Negative stock is visible.<br />Nothing is silently corrected.</p></div>
      <ItemList items={data.items} />
      <div className="overlay-grid"><section className="overlay-panel pending-panel"><h2><span className="status-dot pending-dot" />Pending on this device</h2><p>Saved locally. Not part of confirmed stock.</p>
      <PendingOverlayList events={overlays.data?.pending ?? []} />
      </section><section className="overlay-panel"><h2><span className="status-dot danger-dot" />Quarantined records</h2><p>Rejected by the server. Kept here with the reason.</p>
      <QuarantinedList events={overlays.data?.quarantined ?? []} /></section></div>
    </main>
  )
}
