/**
 * ItemHistoryPage
 * Route          : /items/:itemId/history
 * Responsibility : read-only browse of the append-only movement log for one item (endpoint 6).
 * API calls      : useEventHistory (GET /events?item_id=...)
 * Auth required  : Yes
 */

import { useParams } from 'react-router-dom'
import EventList from '../../components/events/EventList'
import LoadingSpinner from '../../components/common/LoadingSpinner'
import ErrorMessage from '../../components/common/ErrorMessage'
import EmptyState from '../../components/common/EmptyState'
import { useEventHistory } from '../../hooks/useEvents'

export default function ItemHistoryPage() {
  const { itemId } = useParams<{ itemId: string }>()
  const { data, isLoading, isError, error } = useEventHistory({ item_id: itemId, limit: 100 })
  if (isLoading) return <LoadingSpinner />
  if (isError) return <ErrorMessage message={error?.message} />
  if (!data || data.events.length === 0) return <EmptyState message="No movements recorded for this item yet." />
  return (
    <main className="workspace">
      <p className="eyebrow">FIELD OPERATIONS / SUPPLY LEDGER</p>
      <h1>Movement history</h1>
      <p>The log is append-only: records are never updated or deleted, anywhere.</p>
      <EventList events={data.events} />
    </main>
  )
}
